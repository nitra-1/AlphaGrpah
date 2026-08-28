package com.alphagraph.ownership.deals;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * Plain, unconditional INSERT - {@code ownership.deal_materiality} is append-only, one row per
 * scored deal, never updated in place (see V6's migration comment). No {@code ON CONFLICT}: the
 * caller ({@link DealMaterialityScoringOrchestrator}) only ever scores a deal that doesn't already
 * have a materiality row, and the table's own {@code ux_deal_materiality_discovered_deal} unique
 * constraint is the DB-level backstop against a concurrent double-run, not something this writer
 * is expected to absorb silently.
 */
@Component
class DealMaterialityWriter {

    private final JdbcTemplate jdbcTemplate;

    DealMaterialityWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void write(DealMaterialityResult result) {
        jdbcTemplate.update(
            """
            INSERT INTO ownership.deal_materiality (
                id, discovered_deal_id, symbol, deal_date, deal_value, adtv_20, deal_to_adtv_ratio, deal_direction,
                same_side_client_deal_count_20cd, distinct_same_side_clients_20cd, distinct_buyers_20cd, distinct_sellers_20cd,
                materiality_score, materiality_level,
                reported_buy_value_20cd, reported_sell_value_20cd, reported_net_flow_value_20cd, reported_net_flow_ratio, reported_flow_state,
                rule_version, computed_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(), result.discoveredDealId(), result.symbol(), result.dealDate(), result.dealValue(),
            result.adtv20(), result.dealToAdtvRatio(), result.direction(),
            result.sameSideClientDealCount20CalendarDays(), result.distinctSameSideClients20CalendarDays(),
            result.distinctBuyers20CalendarDays(), result.distinctSellers20CalendarDays(),
            result.materialityScore(), result.materialityLevel(),
            result.reportedBuyValue20CalendarDays(), result.reportedSellValue20CalendarDays(),
            result.reportedNetFlowValue20CalendarDays(), result.reportedNetFlowRatio(), result.reportedFlowState(),
            result.ruleVersion(), Timestamp.from(result.computedAt())
        );
    }
}
