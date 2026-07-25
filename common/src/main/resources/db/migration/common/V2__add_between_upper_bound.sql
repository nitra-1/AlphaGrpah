-- The BETWEEN operator needs two bounds, but V1 only gave rule_conditions a single
-- threshold column. threshold is the lower bound for BETWEEN; upper_bound is only
-- populated (and required) when operator = 'BETWEEN'.

ALTER TABLE common.rule_conditions ADD COLUMN upper_bound numeric;

ALTER TABLE common.rule_conditions
    ADD CONSTRAINT ck_rule_conditions_between_upper_bound
    CHECK (operator != 'BETWEEN' OR upper_bound IS NOT NULL);
