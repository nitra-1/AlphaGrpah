-- Capital Allocation evidence (Multibagger Discovery Stage 1, Tier 4 of the remaining 7 evidence
-- families - see claude.md). Reuses the already-live corporate.corporate_actions data via a new,
-- additive, unfiltered CorporateActionsReader.findAllActions(instrumentId) - no new ingestion.
--
-- RIGHTS is deliberately not labeled a dilution metric here: a rights issue offers existing
-- shareholders participation, so counting it under a "dilution" name would overstate what's
-- actually known without pricing/size/take-up context this schema doesn't capture. It's counted
-- under EQUITY_RAISE_EVENT_COUNT_180D instead - a neutral capital-raise signal. QIP/preferential
-- allotment/warrants aren't in corporate_actions' action_type CHECK constraint yet (only
-- DIVIDEND/BONUS/SPLIT/RIGHTS/BUYBACK) - a real, disclosed schema gap, not attempted this pass.
--
-- value/prior_value/change/velocity_per_day are plain integer event counts, not numeric(x,y) like
-- every other evidence family's continuous quantities - a deliberate, unit-appropriate deviation,
-- since a rolling count of real events is always a whole number. Unlike Ownership/Market/Financial,
-- there is no "no prior observation" case here: an instrument with zero actions of a type in the
-- trailing window has a real value of 0, not a missing one - evidence is only computed at all for a
-- (instrument, metric) pair once that action type has occurred for it at least once, ever.
CREATE TABLE corporate.transformation_evidence (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id     uuid NOT NULL,
    symbol            text NOT NULL,
    metric_name       varchar(40) NOT NULL,
    as_of_date        date NOT NULL,
    prior_as_of_date  date NOT NULL,
    value             integer NOT NULL,
    prior_value       integer NOT NULL,
    change            integer NOT NULL,
    velocity_per_day  integer NOT NULL,
    persistence_days  integer NOT NULL DEFAULT 0,
    confidence        numeric(5, 2) NOT NULL,
    window_days       integer NOT NULL DEFAULT 180,
    source            varchar(30) NOT NULL DEFAULT 'CORPORATE_ACTIONS',
    rule_version      integer NOT NULL DEFAULT 1,
    computed_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_corporate_transformation_evidence_metric CHECK (metric_name IN (
        'BUYBACK_EVENT_COUNT_180D', 'EQUITY_RAISE_EVENT_COUNT_180D'
    )),
    CONSTRAINT ux_corporate_transformation_evidence_instrument_metric_date UNIQUE (instrument_id, metric_name, as_of_date)
);

CREATE INDEX ix_corporate_transformation_evidence_instrument_id ON corporate.transformation_evidence (instrument_id);
