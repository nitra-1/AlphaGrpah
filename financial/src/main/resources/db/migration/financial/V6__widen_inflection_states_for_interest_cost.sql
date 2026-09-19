-- Adds INTEREST_COST_DECLINING (Balance-Sheet family, docs/007_Stage2_Inflection_Specification.md
-- §8) as a 7th state on the existing financial.inflection_states table - INTEREST_EXPENSE lives in
-- the same financial.transformation_evidence table, same quarterly cadence, same period_end-based
-- as_of_date correction already in place, so it's folded into this table rather than a new one.
ALTER TABLE financial.inflection_states DROP CONSTRAINT ck_financial_inflection_states_state;

ALTER TABLE financial.inflection_states ADD CONSTRAINT ck_financial_inflection_states_state CHECK (primary_state IN (
    'NO_CLEAR_SIGNAL', 'REVENUE_ACCELERATION', 'PAT_ACCELERATION', 'STRUCTURAL_MARGIN_EXPANSION',
    'OPERATING_LEVERAGE_INFLECTION', 'EARNINGS_INFLECTION_CONVERGENCE', 'INTEREST_COST_DECLINING'
));
