# ADR-0002: Offline-First with Room as Single Source of Truth

**Context:** Campus Wi-Fi is unreliable; UI must never call remote directly.
**Decision:** Room = SSOT; UI observes Room Flows only. Generic `SyncEngine` (delta `updatedSince`, pending-queue with clientOpId idempotency, exponential backoff, optimistic update, 409 server-win conflict + audit).
**Alternatives:** Cache-then-network per screen (N one-off sync impls); Firebase (vendor lock-in, no RBAC control).
**Consequences:** Every feature reuses one sync contract; conflict strategy is uniform and auditable.
