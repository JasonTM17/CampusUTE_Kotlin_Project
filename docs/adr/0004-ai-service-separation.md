# ADR-0004: AI Service Separation (FastAPI + OpenAI Agents SDK)

**Context:** Android must never hold AI keys; Agents SDK ecosystem is server-side.
**Decision:** Separate Python FastAPI service behind the Kotlin backend gateway. Tools execute as HTTP calls BACK to the core API carrying the END USER's JWT, so authorization is re-verified server-side; LLM is never a security boundary. Default model mode is mock/replay (deterministic CI); live models only when env key present.
**Alternatives:** AI inside the JVM backend (no Agents SDK); direct LLM calls from app (key theft, no authz).
**Consequences:** One authz choke point + audit; AI outage degrades gracefully (drill-tested); per-user cost accounting possible.
