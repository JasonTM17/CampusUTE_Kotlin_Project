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

from . import config, tools

SCHEDULE_INTENT = re.compile(r"(lịch|schedule|học|tiết|phòng|week|tuần)", re.I)
REGULATION_INTENT = re.compile(r"(điều kiện|tốt nghiệp|quy chế|regulation|tín chỉ|credit|chuyển ngành)", re.I)
OTHERS_DATA = re.compile(r"(sinh viên khác|của bạn ([a-z]+ )?\w+|student b|other student|grades of)", re.I)
STOPWORDS = {"là", "gì", "của", "và", "cho", "tôi", "có", "the", "what", "is", "of", "my"}
# Mock-mode instruction firewall: imperative/role-override phrasing inside
# documents is untrusted content — it must never be selected as an answer
# sentence. The live LLM path enforces the same rule via the system prompt.
INJECTION_RE = re.compile(r"(ignore|instructions|reveal|admin|bypass|quên (?:tất )?cả|hãy tự|tự cho)", re.I)


def route(message: str) -> str:
    if REGULATION_INTENT.search(message):
        return "REGULATION"
    return "SCHEDULE" if SCHEDULE_INTENT.search(message) else "REGULATION"


def answer(message: str, user_jwt: str, enrolled_course_codes: list[str]) -> dict:
    """Returns {answer, citations:[{document,page,excerpt}], tools:[names]}."""
    # Cross-student probing is refused before any tool call (defense in depth;
    # the backend tool layer enforces the same rule regardless).
    if OTHERS_DATA.search(message):
        return {
            "answer": "Tôi chỉ có thể truy vấn dữ liệu của chính bạn, dựa trên quyền đã xác thực.",
            "citations": [],
            "tools": [],
        }

    if route(message) == "SCHEDULE":
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
    query_tokens = set(re.findall(r"[a-zà-ỹ0-9]+", message.lower())) - STOPWORDS
    scored = []
    for idx, hit in enumerate(hits):
        content = hit["excerpt"]
        for sentence in re.split(r"(?<=[.!?])\s+", content):
            tokens = set(re.findall(r"[a-zà-ỹ0-9]+", sentence.lower()))
            overlap = len(tokens & query_tokens)
            if overlap == 0 or INJECTION_RE.search(sentence):
                continue  # instruction-like sentences are never answer material
            scored.append((overlap, idx, sentence.strip()))
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
