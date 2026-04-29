-- =============================================
-- V2: Create dead_letter_jobs table
-- Stores jobs that have exhausted all retry attempts
-- =============================================

CREATE TABLE dead_letter_jobs (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id          UUID            NOT NULL,
    type            VARCHAR(100)    NOT NULL,
    payload         JSONB,
    priority        VARCHAR(20)     NOT NULL,
    retry_count     INTEGER         NOT NULL,
    max_retries     INTEGER         NOT NULL,
    error_message   TEXT,
    stack_trace     TEXT,
    original_topic  VARCHAR(100),
    failed_at       TIMESTAMP       NOT NULL DEFAULT now(),

    CONSTRAINT fk_dead_letter_job
        FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE CASCADE
);

CREATE INDEX idx_dead_letter_job_id    ON dead_letter_jobs (job_id);
CREATE INDEX idx_dead_letter_failed_at ON dead_letter_jobs (failed_at);
