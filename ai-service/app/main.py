"""CampusUTE AI service — Phase 1 skeleton.

Health/readiness only; agents + RAG land in Phase 5 (plan K3: the skeleton
ships inside compose profile `core` so the AI boundary exists from day one).
"""
from fastapi import FastAPI

app = FastAPI(title="CampusUTE AI Service", version="0.1.0")

MOCK_MODE = True  # live models only when OPENAI_API_KEY is configured (Phase 5)


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "service": "ai-service", "mock_mode": MOCK_MODE}


@app.get("/ready")
def ready() -> dict:
    return {"status": "ready", "checks": {"self": "ok"}}
