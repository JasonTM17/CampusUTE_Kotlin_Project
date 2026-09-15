-- CampusUTE V2: schedule domain (courses, sections, enrollments, sessions)
CREATE TABLE courses (
    id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code    VARCHAR(20)  NOT NULL UNIQUE,
    name    VARCHAR(255) NOT NULL,
    credits INT          NOT NULL DEFAULT 3
);

CREATE TABLE lecturers (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID REFERENCES users (id),
    full_name  VARCHAR(255) NOT NULL,
    department VARCHAR(100)
);

CREATE TABLE class_sections (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id    UUID        NOT NULL REFERENCES courses (id),
    lecturer_id  UUID        NOT NULL REFERENCES lecturers (id),
    section_code VARCHAR(30) NOT NULL UNIQUE,
    semester     VARCHAR(30) NOT NULL,
    building     VARCHAR(50) NOT NULL,
    room         VARCHAR(30) NOT NULL
);

CREATE TABLE enrollments (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id       UUID NOT NULL REFERENCES users (id),
    class_section_id UUID NOT NULL REFERENCES class_sections (id),
    enrolled_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (student_id, class_section_id)
);
CREATE INDEX idx_enrollments_student ON enrollments (student_id);

-- Weekly recurring meetings inside a date window (expanded server-side).
CREATE TABLE schedule_sessions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_section_id UUID   NOT NULL REFERENCES class_sections (id),
    day_of_week      SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    start_time       TIME   NOT NULL,
    end_time         TIME   NOT NULL,
    start_date       DATE   NOT NULL,
    end_date         DATE   NOT NULL
);
CREATE INDEX idx_schedule_section ON schedule_sessions (class_section_id);

-- ---- synthetic demo seed (dev only, no real HCMUTE data) ----
INSERT INTO courses (code, name, credits) VALUES
    ('SE104', 'Software Engineering', 3),
    ('DBMS311', 'Database Systems', 3),
    ('NET325', 'Computer Networks', 3),
    ('OS321', 'Operating Systems', 3),
    ('AI410', 'Artificial Intelligence', 3);

INSERT INTO lecturers (full_name, department) VALUES
    ('Trần Thị Bích', 'Công nghệ Thông tin'),
    ('Lê Văn Cường', 'Công nghệ Thông tin');

INSERT INTO class_sections (course_id, lecturer_id, section_code, semester, building, room)
SELECT c.id, l.id, 'SEC-' || c.code || '-01', 'HK1-2026-2027', 'A', 'A2-0' || ROW_NUMBER() OVER (ORDER BY c.code)
FROM courses c CROSS JOIN lecturers l WHERE l.full_name = 'Trần Thị Bích';

INSERT INTO enrollments (student_id, class_section_id)
SELECT u.id, cs.id
FROM users u, class_sections cs
WHERE u.email = 'student@demo.campusute.vn';

-- Mon..Sat weekly sessions for the current term window.
-- Deliberate overlap: Tuesday (day 2) SE104 & DBMS311 share 08:00-09:30.
INSERT INTO schedule_sessions (class_section_id, day_of_week, start_time, end_time, start_date, end_date)
SELECT cs.id,
       CASE c.code
           WHEN 'SE104' THEN 2 WHEN 'DBMS311' THEN 2 WHEN 'NET325' THEN 3
           WHEN 'OS321' THEN 4 ELSE 6 END::SMALLINT,
       CASE WHEN c.code IN ('SE104', 'DBMS311') THEN TIME '08:00' ELSE TIME '09:45' END,
       CASE WHEN c.code IN ('SE104', 'DBMS311') THEN TIME '09:30' ELSE TIME '11:15' END,
       CURRENT_DATE - 14, CURRENT_DATE + 60
FROM courses c JOIN class_sections cs ON cs.course_id = c.id;
