"""Campus orchestrator: intent router + agents with mandatory citations.

Mock mode is deterministic (no external key): routing uses keyword intent
scoring over a structured schema, and RAG answers are extractive — every
sentence is backed by a retrieved chunk + citation. Live LLM swaps in behind
the same contract when OPENAI_API_KEY is present.

Security invariants (plan Phase 5):
- tool calls carry the END USER's JWT; backend re-verifies (403 => refusal)
- retrieved documents are UNTRUSTED DATA: content never changes routing or
  tool selection, and instruction-like text inside documents is ignored
- permission filtering happens in SQL BEFORE context assembly
"""
import re
import unicodedata

from . import config, tools

def _deaccent(text: str) -> str:
    """Vietnamese users often type without diacritics ('hoc' for 'học',
    'd' for 'đ'); intent matching runs on the deaccented form. 'đ' has no
    NFD decomposition so it is mapped explicitly."""
    normalized = unicodedata.normalize("NFD", text.lower()).replace("đ", "d")
    return "".join(c for c in normalized if unicodedata.category(c) != "Mn")

SCHEDULE_INTENT = re.compile(r"(lich|schedule|hoc|tiet|phong|week|tuan)", re.I)
GRADES_INTENT = re.compile(r"(bang diem|xem diem|diem mon|diem midterm|diem final|diem assignment|ket qua hoc tap|gpa|da co diem|co diem)", re.I)
REGULATION_INTENT = re.compile(r"(dieu kien|tot nghiep|quy che|regulation|tin chi|credit|chuyen nganh|thi lai|hoc phi|tich luy|hoc bong|no mon)", re.I)
LIBRARY_INTENT = re.compile(r"(thu vien|library|sach|tai lieu tham khao|muon|de thi)", re.I)
EVENT_INTENT = re.compile(r"(su kien|event|clb|cau lac bo|workshop|seminar)", re.I)
CAREER_INTENT = re.compile(r"(viec lam|intern|thuc tap|career|cv|doanh nghiep)", re.I)
SERVICE_INTENT = re.compile(r"(ticket|ho tro|support|su co|bao hong)", re.I)
OTHERS_DATA = re.compile(r"(sinh vien khac|hoc ba|student b|other student|grades of|ban cung lop)", re.I)
STOPWORDS = {"la", "gi", "cua", "va", "cho", "toi", "co", "the", "what", "is", "of", "my"}
# Mock-mode instruction firewall: imperative/role-override phrasing inside
# documents is untrusted content — it must never be selected as an answer
# sentence. The live LLM path enforces the same rule via the system prompt.
INJECTION_RE = re.compile(r"(ignore|instructions|reveal|admin|bypass|quen (?:tat )?ca|hay tu|tu cho)", re.I)

# Agent registry: each intent maps to an agent with its OWN tool allowlist.
# Agents not backed by a dedicated backend module (library/career/service)
# answer from the PUBLIC knowledge corpus only — they never gain data tools.


def route(message: str) -> str:
    plain = _deaccent(message)
    # Any other-student data probe is refused before routing (defense in
    # depth; the backend tool layer enforces the same rule regardless).
    if OTHERS_DATA.search(plain):
        return "REFUSE_CROSS_STUDENT"
    if REGULATION_INTENT.search(plain):
        return "REGULATION"
    if GRADES_INTENT.search(plain):
        return "GRADES"
    if SCHEDULE_INTENT.search(plain):
        return "SCHEDULE"
    if LIBRARY_INTENT.search(plain) or EVENT_INTENT.search(plain) or CAREER_INTENT.search(plain) or SERVICE_INTENT.search(plain):
        return "KNOWLEDGE"  # library/career/service agents: public corpus only
    return "REGULATION"


def answer(message: str, user_jwt: str, enrolled_course_codes: list[str]) -> dict:
    """Returns {answer, citations:[{document,page,excerpt}], tools:[names]}."""
    if route(message) == "REFUSE_CROSS_STUDENT":
        return {
            "answer": "Tôi chỉ có thể truy vấn dữ liệu của chính bạn, dựa trên quyền đã xác thực.",
            "citations": [],
            "tools": [],
        }

    agent = route(message)
    if agent == "GRADES":
        result = tools.get_my_grades(user_jwt)
        if "error" in result:
            return {
                "answer": "Tôi không truy vấn được điểm của bạn (quyền bị từ chối bởi hệ thống).",
                "citations": [],
                "tools": ["get_my_grades"],
            }
        rows = [
            f"- {c['courseCode']}: " + ", ".join(f"{g['component']} {g['score']}" for g in c.get("components", []))
            for c in result["courses"]
        ]
        return {
            "answer": ("Điểm các môn của bạn:\n" + "\n".join(rows)) if rows else "Chưa có điểm nào được ghi nhận.",
            "citations": [],
            "tools": ["get_my_grades"],
        }

    if agent == "SCHEDULE":
        result = tools.get_my_schedule(user_jwt)
        if "error" in result:
            return {
                "answer": "Tôi không truy vấn được lịch học của bạn (quyền bị từ chối bởi hệ thống).",
                "citations": [],
                "tools": ["get_my_schedule"],
            }
        lines = [f"- {s['date']} {s['time']} · {s['course']} · phòng {s['room']}" for s in result["sessions"]]
        answer_text = (f"Bạn có {result['count']} tiết trong 7 ngày tới:\n" + "\n".join(lines)) if lines else "Bạn không có tiết nào trong 7 ngày tới."
        return {"answer": answer_text, "citations": [], "tools": ["get_my_schedule"]}

    # REGULATION agent: RAG with permission-filtered retrieval + citations
    hits = tools.search_regulations(message, enrolled_course_codes)
    if not hits:
        return {
            "answer": "Tôi chưa tìm thấy thông tin chính thức đủ chắc chắn trong kho dữ liệu.",
            "citations": [],
            "tools": ["search_regulations"],
        }
    # Extractive answering: only sentences that actually overlap the query are
    # quoted, so instruction-like text inside documents never becomes the answer.
    query_tokens = set(re.findall(r"[a-z0-9]+", _deaccent(message))) - STOPWORDS
    scored = []
    for idx, hit in enumerate(hits):
        # Score on deaccented text; quote the ORIGINAL sentence verbatim.
        pairs = list(zip(re.split(r"(?<=[.!?])\s+", hit["excerpt"]),
                         re.split(r"(?<=[.!?])\s+", _deaccent(hit["excerpt"]))))
        for original, plain in pairs:
            tokens = set(re.findall(r"[a-z0-9]+", plain))
            overlap = len(tokens & query_tokens)
            if overlap == 0 or INJECTION_RE.search(plain):
                continue  # instruction-like sentences are never answer material
            scored.append((overlap, idx, original.strip()))
    scored.sort(key=lambda t: (-t[0], t[1]))
    per_doc: dict[int, list[str]] = {}
    for _, idx, sentence in scored:
        sentences = per_doc.setdefault(idx, [])
        if len(sentences) < 2:  # top-2 spans per source keep answers complete
            sentences.append(sentence)
        if sum(len(v) for v in per_doc.values()) >= 4:
            break
    if not per_doc:
        return {
            "answer": "Tôi chưa tìm thấy thông tin chính thức đủ chắc chắn trong kho dữ liệu.",
            "citations": [],
            "tools": ["search_regulations"],
        }
    parts, cited_idx = [], []
    i = 0
    for idx in sorted(per_doc):
        for sentence in per_doc[idx]:
            i += 1
            parts.append(f"[{i}] {sentence}")
            if idx not in cited_idx:
                cited_idx.append(idx)
    return {
        "answer": "Theo tài liệu chính thức trong kho kiến thức:\n\n" + "\n\n".join(parts),
        "citations": [
            {"document": hits[idx]["document"], "page": hits[idx]["page"], "excerpt": hits[idx]["excerpt"], "source": hits[idx]["source"]}
            for idx in cited_idx
        ],
        "tools": ["search_regulations"],
    }
