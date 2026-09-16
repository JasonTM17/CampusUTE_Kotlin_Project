"""Deterministic hash embeddings (mock mode) — no external key required.

Real models (text-embedding-3-small via OPENAI_API_KEY) replace this in the
live path; the retrieval/citation contract is identical.
"""
import hashlib

from . import config


def embed(text: str) -> list[float]:
    vec = [0.0] * config.EMBED_DIM
    tokens = _tokenize(text)
    for tok in tokens:
        h = int.from_bytes(hashlib.sha256(tok.encode("utf-8")).digest()[:8], "big")
        vec[h % config.EMBED_DIM] += 1.0
        vec[(h >> 8) % config.EMBED_DIM] += 0.5  # second hash position: bigram-ish signal
    norm = sum(v * v for v in vec) ** 0.5 or 1.0
    return [v / norm for v in vec]


def _tokenize(text: str) -> list[str]:
    lowered = text.lower()
    return [t for t in "".join(c if c.isalnum() else " " for c in lowered).split() if len(t) > 1]
