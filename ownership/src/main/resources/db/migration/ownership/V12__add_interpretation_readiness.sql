-- Real design gap caught during the LENSKART investigation: NO_CLEAR_SIGNAL currently means two
-- different things with no way to tell them apart - "genuinely balanced/ambiguous activity" vs.
-- "materiality couldn't be computed yet, so the gates that would have fired never got a chance."
-- interpretation_readiness makes that distinction explicit and independent of whatever
-- institutional_state/event_structure the interpretation actually lands on - a symbol that reaches
-- MULTI_INSTITUTION_BUYING despite some deals in its window still being unscored is still honestly
-- flagged PENDING_DATA, not just the ones that land on NO_CLEAR_SIGNAL.
ALTER TABLE ownership.institutional_interpretations
    ADD COLUMN interpretation_readiness varchar(20) NOT NULL DEFAULT 'READY'
    CONSTRAINT ck_institutional_interpretations_readiness CHECK (interpretation_readiness IN ('READY', 'PENDING_DATA'));
