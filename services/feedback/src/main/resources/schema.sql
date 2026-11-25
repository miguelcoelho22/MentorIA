CREATE TABLE IF NOT EXISTS feedback_requests (
    request_id UUID PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS feedbacks (
    request_id UUID PRIMARY KEY REFERENCES feedback_requests(request_id),
    student_id UUID NOT NULL,
    essay_id UUID NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED')),
    total_score INTEGER NOT NULL CHECK (total_score BETWEEN 0 AND 1000),
    published_at TIMESTAMP WITH TIME ZONE,
    CHECK ((status = 'PUBLISHED') = (published_at IS NOT NULL))
);

CREATE INDEX IF NOT EXISTS feedbacks_student_idx ON feedbacks(student_id, status, published_at);

CREATE TABLE IF NOT EXISTS competency_scores (
    request_id UUID NOT NULL REFERENCES feedbacks(request_id),
    competency INTEGER NOT NULL CHECK (competency BETWEEN 1 AND 5),
    score INTEGER NOT NULL CHECK (score BETWEEN 0 AND 200),
    comment TEXT,
    PRIMARY KEY (request_id, competency)
);

CREATE TABLE IF NOT EXISTS outbox_events (
    event_id UUID PRIMARY KEY,
    request_id UUID NOT NULL UNIQUE REFERENCES feedbacks(request_id),
    event_type TEXT NOT NULL,
    payload TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PUBLISHED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP WITH TIME ZONE,
    CHECK ((status = 'PUBLISHED') = (published_at IS NOT NULL))
);

CREATE INDEX IF NOT EXISTS feedback_outbox_pending_idx ON outbox_events(status, created_at, event_id);
