-- Traces a pipeline execution back to the request that triggered it, per docs/005_Deployment.md
-- §5 ("correlation id (X-Request-Id) propagated from api through to pipeline executions
-- triggered by an API call"). Null for cron-triggered runs that generate their own id, or for
-- rows written before this column existed.
ALTER TABLE scheduler.pipeline_executions ADD COLUMN correlation_id text;

CREATE INDEX ix_pipeline_executions_correlation_id ON scheduler.pipeline_executions (correlation_id);
