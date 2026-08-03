-- Module 2.4: Order Book Engine. A pure rule engine (common.rules.RuleSet-driven, unlike
-- Corporate Event Engine's simple topic matching) reading the canonical facts Module 2.2's
-- Document Intelligence Engine already extracted - never re-parses documents or calls Claude.

-- One order-lifecycle event, parsed from a KNOWLEDGE_EXTRACTED document's facts (customer,
-- orderValue, businessUnit, executionStart/End, orderScope, orderSector, orderRecurrence,
-- orderLifecycleStage - the vocabulary corporate.knowledge.DocumentIntelligenceEngine's prompt
-- asks for). A document without an orderlifecyclestage fact at all simply produces no ledger row -
-- most documents aren't order-related, same "zero is a valid outcome" precedent as
-- corporate_events. order_value_crore is normalized to crore at parse time (from whatever unit the
-- extraction returned) so the aggregation engine can sum/compare values uniformly without
-- re-parsing units on every run.
CREATE TABLE corporate.order_book_ledger (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id         uuid NOT NULL REFERENCES corporate.documents (id) ON DELETE CASCADE,
    instrument_id       uuid NOT NULL,
    symbol              varchar(20) NOT NULL,
    customer            text,
    order_value_crore   numeric(14,2),
    business_unit       varchar(100),
    execution_start     varchar(20),
    execution_end       varchar(20),
    order_scope         varchar(10),
    order_sector        varchar(10),
    order_recurrence    varchar(10),
    lifecycle_stage     varchar(20) NOT NULL,
    detected_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_order_book_ledger_scope CHECK (order_scope IS NULL OR order_scope IN ('DOMESTIC', 'EXPORT')),
    CONSTRAINT ck_order_book_ledger_sector CHECK (order_sector IS NULL OR order_sector IN ('GOVERNMENT', 'PRIVATE')),
    CONSTRAINT ck_order_book_ledger_recurrence CHECK (order_recurrence IS NULL OR order_recurrence IN ('RECURRING', 'ONE_TIME')),
    CONSTRAINT ck_order_book_ledger_lifecycle_stage CHECK (lifecycle_stage IN ('NEW_ORDER', 'TENDER_WIN', 'EXECUTION_UPDATE', 'CANCELLATION', 'COMPLETION'))
);

CREATE INDEX ix_order_book_ledger_instrument_id ON corporate.order_book_ledger (instrument_id);
CREATE INDEX ix_order_book_ledger_document_id ON corporate.order_book_ledger (document_id);

-- One point-in-time aggregation snapshot per instrument. order_book_growth_pct is NULL on an
-- instrument's first-ever snapshot - there is no prior value to compare against, and this is left
-- genuinely absent rather than fabricated as 0%.
CREATE TABLE corporate.order_book_snapshots (
    id                          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id               uuid NOT NULL,
    symbol                      varchar(20) NOT NULL,
    as_of_date                  date NOT NULL,
    current_order_book_crore    numeric(14,2) NOT NULL,
    order_book_growth_pct       numeric(7,2),
    execution_visibility_years  numeric(5,2) NOT NULL,
    order_count                 integer NOT NULL,
    order_quality               varchar(10) NOT NULL,
    quality_score               numeric(5,2) NOT NULL,
    confidence                  numeric(5,2) NOT NULL,
    rule_set_version            integer NOT NULL,
    computed_at                 timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_order_book_snapshots_quality CHECK (order_quality IN ('EXCELLENT', 'GOOD', 'FAIR', 'POOR')),
    CONSTRAINT ux_order_book_snapshots_instrument_date UNIQUE (instrument_id, as_of_date)
);

CREATE INDEX ix_order_book_snapshots_instrument_id ON corporate.order_book_snapshots (instrument_id);

-- Discrete, dashboard-facing signals. MARGIN_IMPROVING (named in the roadmap's signal list) is
-- deliberately not modeled - no margin data source is extracted anywhere in this pipeline yet
-- (Module 2.2's canonical facts don't include margin figures), so fabricating this signal from
-- order-value trends alone would be a real stretch, not a genuine derivation - a disclosed gap,
-- same pattern as Risk Engine's absent Event Risk domain (Module 1.9).
CREATE TABLE corporate.order_book_signals (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id       uuid NOT NULL,
    symbol              varchar(20) NOT NULL,
    signal_type         varchar(20) NOT NULL,
    related_order_id    uuid REFERENCES corporate.order_book_ledger (id) ON DELETE SET NULL,
    detail              text,
    detected_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_order_book_signals_type CHECK (signal_type IN (
        'LARGE_ORDER', 'REPEAT_CUSTOMER', 'EXECUTION_DELAY', 'ORDER_CANCELLATION'
    ))
);

CREATE INDEX ix_order_book_signals_instrument_id ON corporate.order_book_signals (instrument_id);
