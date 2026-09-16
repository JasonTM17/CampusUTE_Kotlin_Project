"""CampusUTE AI service — orchestrator entrypoints (Phase 5).

POST /chat  {message}  [Authorization: Bearer <user JWT>]
  -> {answer, citations, tools}  — citations mandatory for RAG answers.
POST /ingest {title, content, source?, visibility?, course_code?}  (dev/admin)
"""
from fastapi import FastAPI, Header
from pydantic import BaseModel

from . import agents, config, rag

app = FastAPI(title="CampusUTE AI Service", version="0.5.0")


class ChatRequest(BaseModel):
    message: str


class IngestRequest(BaseModel):
    title: str
    content: str
    source: str | None = None
    visibility: str = "PUBLIC"
    course_code: str | None = None


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "service": "ai-service", "mock_mode": config.MOCK_MODE}


@app.get("/ready")
def ready() -> dict:
    return {"status": "ready", "checks": {"self": "ok"}}


@app.post("/chat")
def chat(body: ChatRequest, authorization: str = Header(default="")) -> dict:
    if not authorization.startswith("Bearer "):
        return {"answer": "Thiếu JWT — tôi không thể gọi công cụ thay bạn.", "citations": [], "tools": []}
    user_jwt = authorization.removeprefix("Bearer ").strip()
    return agents.answer(body.message, user_jwt, enrolled_course_codes=["DBMS311"])


@app.post("/ingest")
def ingest(body: IngestRequest) -> dict:
    chunks = rag.ingest_document(
        title=body.title,
        content=body.content,
        source=body.source,
        visibility=body.visibility,
        course_code=body.course_code,
    )
    return {"status": "indexed", "chunks": chunks}
