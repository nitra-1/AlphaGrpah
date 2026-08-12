-- Tracks every @Scheduled job that isn't already covered by scheduler.pipeline_executions (the 9
-- registered common.etl ScheduledPipeline beans keep using that table). This is for the ~15
-- standalone schedulers - one per domain engine/orchestrator - that call their orchestrator
-- directly with no Pipeline abstraction and previously had zero execution tracking at all.
CREATE TABLE scheduler.job_runs (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    job_name       text NOT NULL,
    status         varchar(20) NOT NULL,
    started_at     timestamptz NOT NULL,
    finished_at    timestamptz,
    summary        text,
    error_message  text,
    CONSTRAINT ck_job_runs_status CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED'))
);

CREATE INDEX ix_job_runs_job_name_started_at ON scheduler.job_runs (job_name, started_at DESC);
