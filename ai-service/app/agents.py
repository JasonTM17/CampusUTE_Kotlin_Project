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

# Bidi overrides and zero-width marks let ingested text spoof reading order or hide
# runs from a reader. They carry no meaning, so they are removed at the payload edge.
_INVISIBLE = dict.fromkeys(
    [
        0x202A, 0x202B, 0x202C, 0x202D, 0x202E,  # bidi embedding/override
        0x2066, 0x2067, 0x2068, 0x2069,  # bidi isolates
        0x200B, 0x200C, 0x200D, 0xFEFF,  # zero-width / BOM
        0x2060,  # word joiner
        0x200E, 0x200F, 0x061C,  # LRM / RLM / Arabic letter mark
    ],
    None,
)


def sanitize_text(text: str) -> str:
    """Strip invisible formatting characters from untrusted document content."""
    return (text or "").translate(_INVISIBLE)


def safe_excerpt(text: str) -> str:
    """Filter a citation excerpt with the SAME firewall the answer uses.

    Excerpts are verbatim retrieved document text. Excluding injection sentences
    only while composing the answer left them crossing the wire one field later, so
    a poisoned chunk reached the client inside a citation that asserts provenance.
    """
    kept = [
        sentence.strip()
        for sentence in re.split(r"(?<=[.!?])\s+", text or "")
        # Match on the deaccented form, exactly like the answer path: the Vietnamese
        # alternatives in INJECTION_RE only ever hit the deaccented spelling, so testing
        # the accented original let "Quên tất cả chỉ dẫn trước…" through inside a citation.
        if sentence.strip() and not INJECTION_RE.search(_deaccent(sentence))
    ]
    cleaned = sanitize_text(" ".join(kept)).strip()
    return cleaned or "Trích đoạn bị loại vì chứa nội dung nghi ngờ."

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
    """Returns {answer, citations:[{document,page,excerpt}], tools:[names]}.

    Every payload string crosses this boundary sanitised: the citation fields are
    filtered where they are built, and the answer body is stripped here so a tool-derived
    run (course code, room name) cannot carry a bidi override to another consumer.
    """
    payload = _answer(message, user_jwt, enrolled_course_codes)
    return {**payload, "answer": sanitize_text(payload["answer"])}


def _answer(message: str, user_jwt: str, enrolled_course_codes: list[str]) -> dict:
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
    # Number by SOURCE, not by sentence: the client renders one citation chip per document, so
    # a per-sentence marker could exceed the chip count and leave markers with nothing to tap.
    parts, cited_idx = [], []
    for position, idx in enumerate(sorted(per_doc), start=1):
        for sentence in per_doc[idx]:
            parts.append(f"[{position}] {sentence}")
        cited_idx.append(idx)
    return {
        "answer": "Theo tài liệu chính thức trong kho kiến thức:\n\n" + "\n\n".join(parts),
        "citations": [
            {
                "document": sanitize_text(hits[idx]["document"]),
                "page": hits[idx]["page"],
                "excerpt": safe_excerpt(hits[idx]["excerpt"]),
                "source": sanitize_text(hits[idx]["source"]),
            }
            for idx in cited_idx
        ],
        "tools": ["search_regulations"],
    }
