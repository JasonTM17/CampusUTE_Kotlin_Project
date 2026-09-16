"""RAG store on PostgreSQL + pgvector.

Documents are chunked with metadata; retrieval filters by VISIBILITY BEFORE
any text reaches the LLM context (plan gate: permission filter precedes
context build). Every hit carries document/page/excerpt for citations.
"""
import json

import psycopg
import psycopg.rows

from . import config, embeddings

_pool = None


def connect():
    global _pool
    if _pool is None:
        _pool = psycopg.connect(config.DATABASE_URL, autocommit=True, row_factory=psycopg.rows.dict_row)
    return _pool


def init_schema():
    conn = connect()
    conn.execute("CREATE EXTENSION IF NOT EXISTS vector")
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS ai_documents (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            title VARCHAR(255) NOT NULL,
            source VARCHAR(255),
            visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC', -- PUBLIC | COURSE | FACULTY
            course_code VARCHAR(30),
            active BOOLEAN NOT NULL DEFAULT TRUE,
            created_at TIMESTAMPTZ NOT NULL DEFAULT now()
        )"""
    )
    conn.execute(
        f"""
        CREATE TABLE IF NOT EXISTS ai_chunks (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            document_id UUID NOT NULL REFERENCES ai_documents (id) ON DELETE CASCADE,
            page INT NOT NULL DEFAULT 1,
            content TEXT NOT NULL,
            embedding vector({config.EMBED_DIM}) NOT NULL
        )"""
    )
    conn.execute("CREATE INDEX IF NOT EXISTS idx_chunks_doc ON ai_chunks (document_id)")


def ingest_document(title: str, content: str, source: str | None = None,
                    visibility: str = "PUBLIC", course_code: str | None = None) -> int:
    init_schema()
    conn = connect()
    row = conn.execute(
        "INSERT INTO ai_documents (title, source, visibility, course_code) VALUES (%s,%s,%s,%s) RETURNING id",
        (title, source, visibility, course_code),
    ).fetchone()
    doc_id = row["id"]
    chunks = _chunk_text(content)
    for i, chunk in enumerate(chunks):
        # pgvector accepts the '[d,d,...]' text literal for vector columns
        conn.execute(
            "INSERT INTO ai_chunks (document_id, page, content, embedding) VALUES (%s,%s,%s,%s::vector)",
            (doc_id, i + 1, chunk, json.dumps(embeddings.embed(chunk))),
        )
    return len(chunks)


def retrieve(query: str, enrolled_course_codes: list[str], k: int = 4) -> list[dict]:
    """Hybrid-lite retrieval: vector similarity + keyword overlap, permission
    filtered IN SQL (PUBLIC always; COURSE only for enrolled codes).
    Candidates are deduplicated per document so multiple chunks of one
    source cannot crowd out other documents."""
    init_schema()
    conn = connect()
    qvec = json.dumps(embeddings.embed(query))
    course_filter = "OR (d.visibility = 'COURSE' AND d.course_code = ANY(%s))" if enrolled_course_codes else ""
    rows = conn.execute(
        f"""
        SELECT c.id, c.page, c.content, d.title, d.source, d.course_code,
               (c.embedding <=> %s::vector) AS vec_dist
        FROM ai_chunks c JOIN ai_documents d ON d.id = c.document_id AND d.active
        WHERE d.visibility = 'PUBLIC' {course_filter}
        ORDER BY vec_dist ASC
        LIMIT %s
        """,
        (qvec, enrolled_course_codes or [], k * 4),
    ).fetchall()
    results, seen_docs = [], set()
    for r in rows:
        doc_key = (r["title"], r["course_code"])
        if doc_key in seen_docs:
            continue
        seen_docs.add(doc_key)
        results.append({
            "document": r["title"],
            "page": r["page"],
            "excerpt": r["content"][:220],
            "source": r["source"],
            "course_code": r["course_code"],
        })
        if len(results) >= k:
            break
    return results


def _chunk_text(text: str) -> list[str]:
    text = " ".join(text.split())
    chunks, start = [], 0
    while start < len(text):
        chunks.append(text[start:start + config.CHUNK_CHARS])
        start += config.CHUNK_CHARS - config.CHUNK_OVERLAP
    return chunks or [""]
