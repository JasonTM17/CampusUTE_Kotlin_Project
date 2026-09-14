# ADR-0003: PostgreSQL + pgvector; Hybrid = vector + tsvector/ts_rank

**Context:** RAG needs vector + lexical search; small ops team.
**Decision:** One PostgreSQL 16 + pgvector; hybrid retrieval = pgvector cosine + tsvector/ts_rank merged by RRF, then rerank. ts_rank is BM25-ish, not true BM25 (W2).
**Alternatives:** Dedicated vector DB (extra ops); OpenSearch (heavy); ParadeDB/pg_search (true BM25 — deferred LATER).
**Consequences:** Minimal operational surface; permission filtering happens in SQL before context build.
