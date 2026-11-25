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
