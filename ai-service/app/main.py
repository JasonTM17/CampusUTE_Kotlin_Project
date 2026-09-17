"""CampusUTE AI service — orchestrator entrypoints (Phase 5/6 + hardening).

POST /chat   {message, enrolled_course_codes?}  [Authorization: Bearer <user JWT>]
             enrolled_course_codes are ONLY honored when the caller presents
             X-Internal-Token matching AI_INGEST_TOKEN (i.e., the gateway).
             Direct callers get PUBLIC documents regardless of claimed codes.
POST /ingest {title, content, source?, visibility?, course_code?}
             [X-Internal-Token required — knowledge-base write protection]
"""
import os

from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel

from . import agents, config, rag

app = FastAPI(title="CampusUTE AI Service", version="0.6.0")

_INGEST_TOKEN = os.environ.get("AI_INGEST_TOKEN", "").strip()


class ChatRequest(BaseModel):
    message: str
    enrolled_course_codes: list[str] = []


class IngestRequest(BaseModel):
    title: str
    content: str
    source: str | None = None
    visibility: str = "PUBLIC"
    course_code: str | None = None


def _require_internal_token(token: str | None) -> None:
    if not _INGEST_TOKEN or token != _INGEST_TOKEN:
        raise HTTPException(status_code=401, detail="internal token required")


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "service": "ai-service", "mock_mode": config.MOCK_MODE}


@app.get("/ready")
def ready() -> dict:
    return {"status": "ready", "checks": {"self": "ok"}}


@app.post("/chat")
def chat(body: ChatRequest, authorization: str = Header(default=""), x_internal_token: str | None = Header(default=None, alias="X-Internal-Token")) -> dict:
    if not authorization.startswith("Bearer "):
        return {"answer": "Thiếu JWT — tôi không thể gọi công cụ thay bạn.", "citations": [], "tools": []}
    user_jwt = authorization.removeprefix("Bearer ").strip()
    trusted = _INGEST_TOKEN and x_internal_token == _INGEST_TOKEN
    codes = body.enrolled_course_codes if trusted else []
    return agents.answer(body.message, user_jwt, codes)


@app.post("/ingest")
def ingest(body: IngestRequest, x_internal_token: str | None = Header(default=None, alias="X-Internal-Token")) -> dict:
    _require_internal_token(x_internal_token)
    if body.visibility not in {"PUBLIC", "COURSE", "FACULTY"}:
        raise HTTPException(status_code=400, detail="visibility phải là PUBLIC/COURSE/FACULTY")
    if len(body.content) > 20_000 or len(body.title) > 255:
        raise HTTPException(status_code=400, detail="Tài liệu quá dài.")
    try:
        chunks = rag.ingest_document(
            title=body.title,
            content=body.content,
            source=body.source,
            visibility=body.visibility,
            course_code=body.course_code,
        )
    except Exception:
        return {"status": "error", "message": "Kho tri thức tạm thời không truy cập được."}
    return {"status": "indexed", "chunks": chunks}
