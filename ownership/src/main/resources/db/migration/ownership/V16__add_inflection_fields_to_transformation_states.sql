-- Stage 2 retrofit (docs/007_Stage2_Inflection_Specification.md §9/§14): the winning state now
-- carries the same level/change/velocity/persistence dimensions every Stage 2 state is meant to
-- have, sourced from whichever EvidenceObservation actually drove that state (see
-- OwnershipTransformationEngine's new driving-metric selection). All nullable except persistence -
-- NO_CLEAR_SIGNAL has no driving metric, an honest "nothing to report", not a guessed one.
ALTER TABLE ownership.transformation_states
    ADD COLUMN driving_metric varchar(40) NULL,
    ADD COLUMN level           numeric(7, 4) NULL,
    ADD COLUMN change          numeric(7, 4) NULL,
    ADD COLUMN velocity_band   varchar(10) NULL,
    ADD COLUMN persistence     integer NOT NULL DEFAULT 0;
