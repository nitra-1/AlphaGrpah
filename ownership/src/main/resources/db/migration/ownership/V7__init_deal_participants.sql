-- Sprint 3 of the bulk/block deal auto-discovery roadmap: Institutional Interpretation Engine.
-- A canonical participant registry resolving the same real-world entity across many deal rows
-- (by exact match on client_name_normalized, see V5) so repetition/breadth/churn can be judged
-- per genuine participant, not per raw string. Deterministic name-pattern classification only -
-- no ML, no fuzzy matching. PROMOTER/PROMOTER_GROUP/STRATEGIC_INVESTOR are deliberately absent -
-- no promoter/shareholding data source exists anywhere in AlphaGraph (confirmed, consistent with
-- Sprint 1's own scoping notes), so classifying a participant as a company's promoter from name
-- text alone would be a fabrication, not a real inference. Deferred to a future sprint once a
-- real promoter data source (e.g. NSE shareholding-pattern filings) exists.
--
-- classification_confidence is not decorative - it feeds directly into interpretation confidence
-- (a symbol dominated by 60-confidence FPI/FII guesses reads less trustworthy than one dominated
-- by 95-confidence "SBI MUTUAL FUND"-style matches). verified_at is reserved for a future manual-
-- verification workflow - nothing in Sprint 3 populates it.
CREATE TABLE ownership.deal_participants (
    id                       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    canonical_name           text NOT NULL,
    normalized_name          text NOT NULL,
    participant_type         varchar(30) NOT NULL DEFAULT 'UNKNOWN',
    classification_source    varchar(30) NOT NULL DEFAULT 'NAME_PATTERN_V1',
    classification_confidence numeric(5, 2) NOT NULL DEFAULT 0,
    verified_at              timestamptz NULL,
    created_at               timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_deal_participants_normalized_name UNIQUE (normalized_name),
    CONSTRAINT ck_deal_participants_type CHECK (participant_type IN (
        'MUTUAL_FUND', 'INSURANCE', 'AIF', 'CORPORATE', 'PROP_DESK', 'QUANT_HFT', 'BROKER',
        'INDIVIDUAL', 'FPI_FII', 'SOVEREIGN_PENSION_FUND', 'UNKNOWN'
    ))
);

-- Seeded only with each participant's own first-seen raw name at creation time - no automatic
-- fuzzy alias-merging ("ABC MUTUAL FUND LTD" vs "ABC MUTUAL FUND" won't collapse), the same known
-- v1 limitation client_name_normalized already carries. Reserved for a future admin-driven merge.
CREATE TABLE ownership.deal_participant_aliases (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    participant_id   uuid NOT NULL REFERENCES ownership.deal_participants (id),
    alias            text NOT NULL,
    normalized_alias text NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_deal_participant_aliases_normalized_alias UNIQUE (normalized_alias)
);

CREATE INDEX ix_deal_participant_aliases_participant ON ownership.deal_participant_aliases (participant_id);
