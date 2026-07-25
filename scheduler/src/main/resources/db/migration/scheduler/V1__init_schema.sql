CREATE SCHEMA IF NOT EXISTS scheduler;

CREATE FUNCTION scheduler.set_updated_at()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

CREATE TABLE scheduler.pipeline_definitions (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name             text NOT NULL UNIQUE,
    module           text NOT NULL,
    cron_expression  text NOT NULL,
    active           boolean NOT NULL DEFAULT true,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now()
);

CREATE TRIGGER trg_pipeline_definitions_updated_at
    BEFORE UPDATE ON scheduler.pipeline_definitions
    FOR EACH ROW EXECUTE FUNCTION scheduler.set_updated_at();

CREATE TABLE scheduler.pipeline_executions (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_id    uuid NOT NULL REFERENCES scheduler.pipeline_definitions (id),
    started_at     timestamptz NOT NULL DEFAULT now(),
    finished_at    timestamptz,
    status         text NOT NULL CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED', 'PARTIAL')),
    rows_read      integer NOT NULL DEFAULT 0,
    rows_accepted  integer NOT NULL DEFAULT 0,
    rows_rejected  integer NOT NULL DEFAULT 0,
    retry_count    integer NOT NULL DEFAULT 0,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now()
);

-- Supports "latest run per pipeline" lookups, per docs/003_Database_Architecture.md §6.
CREATE INDEX ix_pipeline_executions_pipeline_started
    ON scheduler.pipeline_executions (pipeline_id, started_at DESC);

CREATE TRIGGER trg_pipeline_executions_updated_at
    BEFORE UPDATE ON scheduler.pipeline_executions
    FOR EACH ROW EXECUTE FUNCTION scheduler.set_updated_at();

CREATE TABLE scheduler.pipeline_execution_errors (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_execution_id  uuid NOT NULL REFERENCES scheduler.pipeline_executions (id),
    message                text NOT NULL,
    source_reference       text,
    created_at             timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_pipeline_execution_errors_execution_id
    ON scheduler.pipeline_execution_errors (pipeline_execution_id);
