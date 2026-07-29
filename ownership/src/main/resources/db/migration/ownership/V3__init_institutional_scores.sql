-- Module 1.7: the Institutional Engine's output. Answers "what is smart money doing?" from
-- promoter/FII/DII/MF shareholding trend, delivery %, volume, and bulk/block deals.
--
-- *_change_percentage fields are percentage-POINT changes since the prior shareholding period
-- (e.g. 50.48% -> 50.01% is -0.47), not growth rates - nullable because ownership.shareholding_pattern
-- only has a second real period for RELIANCE so far (Module 1.7's research); every other
-- instrument correctly reports STABLE/no-trend rather than a guessed direction.
--
-- avg_delivery_percentage and relative_volume are nullable because they need market's daily bar
-- history (via intelligence, since ownership can't depend on market directly - same
-- docs/001_System_Architecture.md §4 Rule 3 as Module 1.5). net_bulk_deal_quantity is nullable
-- for the same reason ownership.bulk_deals is usually empty for our tracked instruments (see that
-- table's migration) - large/mid-caps rarely generate a reportable bulk/block deal.
CREATE TABLE ownership.institutional_scores (
    id                          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id               uuid NOT NULL,
    as_of_date                  date NOT NULL,
    promoter_status             varchar(20) NOT NULL,
    fii_status                  varchar(20) NOT NULL,
    dii_status                  varchar(20) NOT NULL,
    mf_status                   varchar(20) NOT NULL,
    delivery_status             varchar(20) NOT NULL,
    institutional_behaviour     varchar(30) NOT NULL,
    institutional_score         numeric(5, 2) NOT NULL,
    confidence                  numeric(5, 2) NOT NULL,
    promoter_percentage         numeric(5, 2),
    promoter_change_percentage  numeric(6, 2),
    fii_change_percentage       numeric(6, 2),
    dii_change_percentage       numeric(6, 2),
    mf_change_percentage        numeric(6, 2),
    avg_delivery_percentage     numeric(5, 2),
    relative_volume             numeric(6, 2),
    net_bulk_deal_quantity      bigint,
    rule_set_version            integer NOT NULL,
    computed_at                 timestamptz NOT NULL DEFAULT now(),
    created_at                  timestamptz NOT NULL DEFAULT now(),
    updated_at                  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_institutional_scores_promoter_status CHECK (promoter_status IN ('ACCUMULATING', 'STABLE', 'DISTRIBUTING')),
    CONSTRAINT ck_institutional_scores_fii_status CHECK (fii_status IN ('BUYING', 'STABLE', 'SELLING')),
    CONSTRAINT ck_institutional_scores_dii_status CHECK (dii_status IN ('BUYING', 'STABLE', 'SELLING')),
    CONSTRAINT ck_institutional_scores_mf_status CHECK (mf_status IN ('BUYING', 'STABLE', 'SELLING')),
    CONSTRAINT ck_institutional_scores_delivery_status CHECK (delivery_status IN ('VERY_HIGH', 'HIGH', 'MODERATE', 'LOW')),
    CONSTRAINT ck_institutional_scores_behaviour CHECK (institutional_behaviour IN
        ('STRONG_ACCUMULATION', 'ACCUMULATION', 'NEUTRAL', 'DISTRIBUTION', 'STRONG_DISTRIBUTION')),
    CONSTRAINT ux_institutional_scores_instrument_date UNIQUE (instrument_id, as_of_date)
);

-- instrument_id references reference.instruments by value only — no cross-schema foreign key,
-- per docs/003_Database_Architecture.md §2.
CREATE INDEX ix_institutional_scores_instrument_id ON ownership.institutional_scores (instrument_id);
CREATE INDEX ix_institutional_scores_as_of_date ON ownership.institutional_scores (as_of_date);

CREATE TRIGGER trg_institutional_scores_updated_at
    BEFORE UPDATE ON ownership.institutional_scores
    FOR EACH ROW EXECUTE FUNCTION ownership.set_updated_at();
