# ADR-0001: Monorepo + Backend Modular Monolith

**Context:** 3 codebases (Android/Kotlin, backend/Kotlin, AI/Python), 1 developer, course deadline.

**Decision:** One repo; backend is a Spring Boot modular monolith (package-per-domain), not microservices.

**Alternatives:** Polyrepo (atomic cross-service change impossible); microservices from day 1 (operational cost with no users).

**Consequences:** Atomic commits across Android/backend/AI, one CI; module boundaries enforce future extraction; event bus (outbox) keeps the extraction path open.
