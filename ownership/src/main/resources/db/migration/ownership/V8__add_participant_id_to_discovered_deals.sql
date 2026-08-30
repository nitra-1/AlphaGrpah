-- Sprint 3: links each deal to its resolved participant. Nullable - existing rows are resolved by
-- InstitutionalInterpretationOrchestrator's own self-healing first step (participant
-- classification is Java logic, not SQL-expressible without duplicating the classifier in two
-- languages, so unlike V5's client_name_normalized there's no SQL backfill in this migration).
ALTER TABLE ownership.discovered_deals ADD COLUMN participant_id uuid NULL REFERENCES ownership.deal_participants (id);

CREATE INDEX ix_discovered_deals_participant ON ownership.discovered_deals (participant_id);
