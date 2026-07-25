CREATE SCHEMA IF NOT EXISTS common;

CREATE FUNCTION common.set_updated_at()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

-- Rule Engine (docs/002_Engine_Architecture.md §4)

CREATE TABLE common.rule_definitions (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name           text NOT NULL,
    target_metric  text NOT NULL,
    version        integer NOT NULL,
    active         boolean NOT NULL DEFAULT true,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_rule_definitions_name_version UNIQUE (name, version)
);

-- At most one active version per rule name, per docs/003_Database_Architecture.md §5.
CREATE UNIQUE INDEX ux_rule_definitions_active_name
    ON common.rule_definitions (name)
    WHERE active = true;

CREATE TRIGGER trg_rule_definitions_updated_at
    BEFORE UPDATE ON common.rule_definitions
    FOR EACH ROW EXECUTE FUNCTION common.set_updated_at();

CREATE TABLE common.rule_conditions (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id     uuid NOT NULL REFERENCES common.rule_definitions (id),
    operator    text NOT NULL CHECK (operator IN ('GT', 'LT', 'GTE', 'LTE', 'EQ', 'BETWEEN')),
    threshold   numeric NOT NULL,
    weight      numeric NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_rule_conditions_rule_id ON common.rule_conditions (rule_id);

CREATE TRIGGER trg_rule_conditions_updated_at
    BEFORE UPDATE ON common.rule_conditions
    FOR EACH ROW EXECUTE FUNCTION common.set_updated_at();

-- Data Quality Engine (docs/002_Engine_Architecture.md §3)
-- pipeline_execution_id references scheduler.pipeline_executions by value only —
-- no cross-schema foreign key, per docs/003_Database_Architecture.md §2.

CREATE TABLE common.data_quality_scores (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_execution_id  uuid NOT NULL,
    completeness           numeric(5, 4) NOT NULL CHECK (completeness BETWEEN 0 AND 1),
    duplicate_rate         numeric(5, 4) NOT NULL CHECK (duplicate_rate BETWEEN 0 AND 1),
    missing_field_rate     numeric(5, 4) NOT NULL CHECK (missing_field_rate BETWEEN 0 AND 1),
    validation_error_rate  numeric(5, 4) NOT NULL CHECK (validation_error_rate BETWEEN 0 AND 1),
    score                  numeric(5, 4) NOT NULL CHECK (score BETWEEN 0 AND 1),
    computed_at            timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_data_quality_scores_pipeline_execution_id
    ON common.data_quality_scores (pipeline_execution_id);
