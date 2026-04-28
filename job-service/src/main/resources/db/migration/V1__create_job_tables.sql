-- =============================================
-- V1: Create jobs and job_audit_logs tables
-- =============================================

-- Custom ENUM types for PostgreSQL
CREATE TYPE job_status AS ENUM (
    'PENDING',
    'SCHEDULED',
    'QUEUED',
    'PROCESSING',
    'COMPLETED',
    'FAILED',
    'CANCELLED',
    'RETRYING'
);

CREATE TYPE job_priority AS ENUM (
    'LOW',
    'MEDIUM',
    'HIGH',
    'CRITICAL'
);

-- =============================================
-- Jobs table
-- =============================================
CREATE TABLE jobs (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    type            VARCHAR(100)    NOT NULL,
    payload         JSONB,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    priority        VARCHAR(20)     NOT NULL DEFAULT 'MEDIUM',
    retry_count     INTEGER         NOT NULL DEFAULT 0,
    max_retries     INTEGER         NOT NULL DEFAULT 3,
    created_at      TIMESTAMP       NOT NULL DEFAULT now(),
    scheduled_at    TIMESTAMP,
    completed_at    TIMESTAMP,
    error_message   TEXT,

    CONSTRAINT chk_jobs_status CHECK (status IN (
        'PENDING', 'SCHEDULED', 'QUEUED', 'PROCESSING',
        'COMPLETED', 'FAILED', 'CANCELLED', 'RETRYING'
    )),
    CONSTRAINT chk_jobs_priority CHECK (priority IN (
        'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'
    )),
    CONSTRAINT chk_retry_count CHECK (retry_count >= 0),
    CONSTRAINT chk_max_retries CHECK (max_retries >= 0)
);

-- Indexes for common query patterns
CREATE INDEX idx_jobs_status       ON jobs (status);
CREATE INDEX idx_jobs_priority     ON jobs (priority);
CREATE INDEX idx_jobs_scheduled_at ON jobs (scheduled_at);
CREATE INDEX idx_jobs_type         ON jobs (type);

-- Composite index for the worker polling query:
-- "give me the next PENDING or SCHEDULED job, highest priority first"
CREATE INDEX idx_jobs_status_priority_scheduled
    ON jobs (status, priority DESC, scheduled_at ASC);

-- =============================================
-- Job Audit Logs table
-- =============================================
CREATE TABLE job_audit_logs (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id          UUID            NOT NULL,
    old_status      VARCHAR(20),
    new_status      VARCHAR(20)     NOT NULL,
    timestamp       TIMESTAMP       NOT NULL DEFAULT now(),
    message         TEXT,

    CONSTRAINT fk_audit_job
        FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE CASCADE,

    CONSTRAINT chk_audit_old_status CHECK (old_status IS NULL OR old_status IN (
        'PENDING', 'SCHEDULED', 'QUEUED', 'PROCESSING',
        'COMPLETED', 'FAILED', 'CANCELLED', 'RETRYING'
    )),
    CONSTRAINT chk_audit_new_status CHECK (new_status IN (
        'PENDING', 'SCHEDULED', 'QUEUED', 'PROCESSING',
        'COMPLETED', 'FAILED', 'CANCELLED', 'RETRYING'
    ))
);

CREATE INDEX idx_audit_job_id    ON job_audit_logs (job_id);
CREATE INDEX idx_audit_timestamp ON job_audit_logs (timestamp);
