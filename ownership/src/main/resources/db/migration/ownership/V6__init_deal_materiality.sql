-- Sprint 2 of the bulk/block deal auto-discovery roadmap: one row per scored discovered_deals
-- row - the primary significance measure (Deal Value / 20-trading-day ADTV) plus direction-neutral
-- repetition/breadth, blended into a single 0-100 materiality_score/materiality_level, and a
-- genuinely SEPARATE reported net-flow signal (reported_flow_state) - kept apart deliberately,
-- since two large deals that net to selling would otherwise look identical to genuine buying under
-- a materiality-only view (see ownership.deals.DealMaterialityEngine).
--
-- Append-only, never updated in place: a real historical ledger for future validation of the
-- scoring model, not a "latest score" table like ownership.institutional_scores. Scored exactly
-- once per deal (see DealMaterialityScoringScheduler's "no materiality row yet" driving query) -
-- the UNIQUE constraint on discovered_deal_id is the DB-level backstop for that, defense in depth
-- against a concurrent/double scheduler run, not evidence this table expects repeat scoring.
--
-- reported_flow_state is deliberately named "reported", not "accumulation" - bulk/block deals are
-- disclosed, qualifying participants only; "reported buy value > reported sell value" proves only
-- that the visible deals lean buy-side, not genuine accumulation (see the migration's own naming
-- precedent-setting discussion in claude.md).
CREATE TABLE ownership.deal_materiality (
    id                                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    discovered_deal_id                 uuid NOT NULL REFERENCES ownership.discovered_deals (id),
    symbol                             text NOT NULL,
    deal_date                          date NOT NULL,
    deal_value                         numeric(18, 2) NOT NULL,
    adtv_20                            numeric(18, 2) NOT NULL,
    deal_to_adtv_ratio                 numeric(12, 4) NOT NULL,
    deal_direction                     varchar(4) NOT NULL,
    same_side_client_deal_count_20cd   integer NOT NULL,
    distinct_same_side_clients_20cd    integer NOT NULL,
    distinct_buyers_20cd               integer NOT NULL,
    distinct_sellers_20cd              integer NOT NULL,
    materiality_score                  numeric(5, 2) NOT NULL,
    materiality_level                  varchar(10) NOT NULL,
    reported_buy_value_20cd            numeric(18, 2) NOT NULL,
    reported_sell_value_20cd           numeric(18, 2) NOT NULL,
    reported_net_flow_value_20cd       numeric(18, 2) NOT NULL,
    reported_net_flow_ratio            numeric(8, 4) NOT NULL,
    reported_flow_state                varchar(20) NOT NULL,
    rule_version                       integer NOT NULL,
    computed_at                        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_deal_materiality_direction CHECK (deal_direction IN ('BUY', 'SELL')),
    CONSTRAINT ck_deal_materiality_level CHECK (materiality_level IN ('LOW', 'MEDIUM', 'HIGH', 'VERY_HIGH')),
    CONSTRAINT ck_deal_materiality_flow_state CHECK (
        reported_flow_state IN ('STRONG_NET_BUYING', 'NET_BUYING', 'BALANCED', 'NET_SELLING', 'STRONG_NET_SELLING')
    ),
    CONSTRAINT ux_deal_materiality_discovered_deal UNIQUE (discovered_deal_id)
);

CREATE INDEX ix_deal_materiality_symbol ON ownership.deal_materiality (symbol);
