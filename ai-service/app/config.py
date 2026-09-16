"""AI service configuration (env-driven, secrets only from environment)."""
import os

DATABASE_URL = os.environ.get("AI_DATABASE_URL", "postgresql://campusute:campusute@postgres:5432/campusute")
BACKEND_BASE_URL = os.environ.get("BACKEND_BASE_URL", "http://backend:8080/api/v1")
OPENAI_API_KEY = os.environ.get("OPENAI_API_KEY", "").strip()
EMBED_DIM = 256
CHUNK_CHARS = 600
CHUNK_OVERLAP = 100
MOCK_MODE = not OPENAI_API_KEY
