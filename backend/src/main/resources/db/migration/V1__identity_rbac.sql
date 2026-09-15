-- CampusUTE V1: identity, RBAC, sessions, audit
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    student_code  VARCHAR(20),
    department    VARCHAR(100),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE roles (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE user_roles (
    user_id UUID    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id BIGINT  NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(128) NOT NULL UNIQUE,
    issued_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    replaced_by VARCHAR(128),
    user_agent  VARCHAR(255)
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

CREATE TABLE audit_logs (
    id         BIGSERIAL PRIMARY KEY,
    actor_id   UUID,
    action     VARCHAR(64)  NOT NULL,
    resource   VARCHAR(255),
    result     VARCHAR(16)  NOT NULL,
    trace_id   VARCHAR(64),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_action_time ON audit_logs (action, created_at);

INSERT INTO roles (name) VALUES
    ('STUDENT'), ('LECTURER'), ('DEPARTMENT_ADMIN'),
    ('ACADEMIC_STAFF'), ('ADMIN'), ('SUPER_ADMIN');
