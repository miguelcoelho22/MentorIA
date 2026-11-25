CREATE TABLE IF NOT EXISTS quotas (
    student_id UUID NOT NULL,
    period DATE NOT NULL,
    PRIMARY KEY (student_id, period)
);

CREATE TABLE IF NOT EXISTS quota_reservations (
    request_id UUID PRIMARY KEY,
    student_id UUID NOT NULL,
    period DATE NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('RESERVED', 'CONSUMED', 'RELEASED')),
    FOREIGN KEY (student_id, period) REFERENCES quotas (student_id, period)
);

CREATE INDEX IF NOT EXISTS quota_reservations_student_period_idx
    ON quota_reservations (student_id, period);

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    email TEXT NOT NULL UNIQUE CHECK (email = LOWER(email)),
    password_hash TEXT NOT NULL,
    name TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('STUDENT', 'TEACHER')),
    demo BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS classes (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    teacher_id UUID NOT NULL REFERENCES users (id)
);

CREATE INDEX IF NOT EXISTS classes_teacher_id_idx ON classes (teacher_id);

CREATE TABLE IF NOT EXISTS class_students (
    student_id UUID PRIMARY KEY REFERENCES users (id),
    class_id TEXT NOT NULL REFERENCES classes (id)
);

CREATE INDEX IF NOT EXISTS class_students_class_id_idx ON class_students (class_id);

CREATE TABLE IF NOT EXISTS account_signing_key (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    private_key_pkcs8 TEXT NOT NULL
);
