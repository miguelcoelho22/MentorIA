CREATE SCHEMA IF NOT EXISTS plan_write;
CREATE SCHEMA IF NOT EXISTS plan_read;

CREATE TABLE IF NOT EXISTS plan_write.plan (
    student_id UUID PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS plan_write.task (
    id TEXT PRIMARY KEY,
    student_id UUID NOT NULL REFERENCES plan_write.plan(student_id),
    title TEXT NOT NULL,
    competency INTEGER NOT NULL CHECK (competency BETWEEN 1 AND 5),
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'DONE')),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS task_student_idx ON plan_write.task(student_id);

CREATE TABLE IF NOT EXISTS plan_write.competency_score (
    student_id UUID NOT NULL REFERENCES plan_write.plan(student_id),
    request_id TEXT NOT NULL,
    competency INTEGER NOT NULL CHECK (competency BETWEEN 1 AND 5),
    score INTEGER NOT NULL CHECK (score BETWEEN 0 AND 200),
    published_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (student_id, request_id, competency)
);

CREATE TABLE IF NOT EXISTS plan_write.processed_events (
    event_id TEXT PRIMARY KEY,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS plan_write.domain_event (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    student_id UUID NOT NULL REFERENCES plan_write.plan(student_id),
    event_type TEXT NOT NULL CHECK (event_type IN ('TasksRecommended', 'TaskCompleted', 'ScoreRecorded')),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    projected_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS domain_event_pending_idx ON plan_write.domain_event(projected_at, id);

CREATE TABLE IF NOT EXISTS plan_read.student_plan_view (
    student_id UUID PRIMARY KEY,
    payload TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS plan_read.student_progress_view (
    student_id UUID PRIMARY KEY,
    payload TEXT NOT NULL
);
