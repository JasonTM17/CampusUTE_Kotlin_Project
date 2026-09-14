# ADR-0005: Event-Driven Analytics with Transactional Outbox (Optional Profile)

**Context:** Big-data requirement vs. "local dev must not need heavy stack".
**Decision:** Domain events (versioned schema) -> Transactional Outbox in Postgres -> worker -> Kafka -> ClickHouse -> Grafana dashboards, all behind compose profile `analytics`. Core runs with the profile off; Outbox buffers when Kafka is down (at-least-once).
**Alternatives:** Analytics tables in Postgres (OLAP load on OLTP); always-on Kafka (dev burden).
**Consequences:** No dual-write inconsistency; heavy stack is opt-in; events carry no sensitive payload.
