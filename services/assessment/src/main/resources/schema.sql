CREATE TABLE IF NOT EXISTS themes (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS essays (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL,
    theme_id TEXT NOT NULL REFERENCES themes(id),
    text TEXT NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS essays_student_idx ON essays(student_id, submitted_at);

CREATE TABLE IF NOT EXISTS correction_requests (
    id UUID PRIMARY KEY,
    essay_id UUID NOT NULL REFERENCES essays(id),
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'REJECTED', 'FAILED')),
    reason TEXT CHECK (reason IN ('QUOTA_EXHAUSTED', 'AI_UNAVAILABLE', 'PUBLICATION_ERROR')),
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS correction_requests_essay_idx ON correction_requests(essay_id, status);

CREATE TABLE IF NOT EXISTS saga_start_outbox (
    request_id UUID PRIMARY KEY REFERENCES correction_requests(id),
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS saga_start_outbox_pending_idx ON saga_start_outbox(published_at, created_at, request_id);
