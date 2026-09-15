-- CampusUTE V3: sync-able study tasks + idempotent operation ledger
CREATE TABLE study_tasks (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title      VARCHAR(255) NOT NULL,
    due_date   DATE,
    done       BOOLEAN     NOT NULL DEFAULT FALSE,
    deleted    BOOLEAN     NOT NULL DEFAULT FALSE, -- tombstone for sync
    version    BIGINT      NOT NULL DEFAULT 1,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_tasks_user_updated ON study_tasks (user_id, updated_at);

-- Applied client operations make create-replays idempotent (offline retry).
CREATE TABLE sync_operations (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    client_op_id VARCHAR(64) NOT NULL,
    op_type      VARCHAR(16) NOT NULL,
    applied_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, client_op_id)
);
