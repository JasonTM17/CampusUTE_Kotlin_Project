-- CampusUTE V4: academic core — assignments, grades, attendance, events
CREATE TABLE assignments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    section_id  UUID        NOT NULL REFERENCES class_sections (id),
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    due_at      TIMESTAMPTZ,
    max_score   NUMERIC(5,2) NOT NULL DEFAULT 10
);

CREATE TABLE submissions (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id UUID NOT NULL REFERENCES assignments (id),
    student_id   UUID NOT NULL REFERENCES users (id),
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    note         TEXT,
    UNIQUE (assignment_id, student_id)
);

CREATE TABLE grades (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    course_id UUID        NOT NULL REFERENCES courses (id),
    component VARCHAR(20) NOT NULL CHECK (component IN ('ASSIGNMENT','MIDTERM','FINAL','OTHER')),
    score     NUMERIC(4,2) NOT NULL,
    weight    NUMERIC(4,2) NOT NULL DEFAULT 1,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (student_id, course_id, component)
);
CREATE INDEX idx_grades_student ON grades (student_id);

CREATE TABLE attendance_sessions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    section_id  UUID   NOT NULL REFERENCES class_sections (id),
    lecturer_id UUID   NOT NULL REFERENCES lecturers (id),
    secret      VARCHAR(128) NOT NULL, -- per-session HMAC key (server-side only for verification; rotated per session)
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE attendance_records (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID   NOT NULL REFERENCES attendance_sessions (id),
    student_id UUID   NOT NULL REFERENCES users (id),
    bucket     BIGINT NOT NULL, -- rotated time bucket accepted by the server
    nonce      VARCHAR(64) NOT NULL,
    scanned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (session_id, student_id),
    UNIQUE (nonce)
);
CREATE INDEX idx_attendance_session ON attendance_records (session_id);

CREATE TABLE events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(30) NOT NULL UNIQUE,
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    starts_at   TIMESTAMPTZ NOT NULL,
    location    VARCHAR(255),
    capacity    INT NOT NULL DEFAULT 100
);

CREATE TABLE event_registrations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        UUID NOT NULL REFERENCES events (id),
    student_id      UUID NOT NULL REFERENCES users (id),
    idempotency_key VARCHAR(64) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (event_id, student_id),
    UNIQUE (student_id, idempotency_key)
);

-- synthetic demo content (dev only)
INSERT INTO events (code, title, description, starts_at, location, capacity) VALUES
    ('EVT-2026-CAREER', 'Ngày hội việc làm UTE 2026', 'Gặp gỡ 30 doanh nghiệp công nghệ.', now() + interval '10 days', 'Nhà thi đấu A1', 500),
    ('EVT-2026-AI-SEM', 'Seminar: LLM trong giáo dục', 'Chia sẻ về RAG và trợ lý học tập.', now() + interval '3 days', 'Phòng B4-201', 120);

INSERT INTO grades (student_id, course_id, component, score, weight)
SELECT u.id, c.id, 'MIDTERM', 7.5, 0.3 FROM users u, courses c WHERE u.email = 'student@demo.campusute.vn' AND c.code = 'DBMS311';
INSERT INTO grades (student_id, course_id, component, score, weight)
SELECT u.id, c.id, 'ASSIGNMENT', 8.5, 0.2 FROM users u, courses c WHERE u.email = 'student@demo.campusute.vn' AND c.code = 'DBMS311';
INSERT INTO grades (student_id, course_id, component, score, weight)
SELECT u.id, c.id, 'FINAL', 0, 0.5 FROM users u, courses c WHERE u.email = 'student@demo.campusute.vn' AND c.code = 'DBMS311';
