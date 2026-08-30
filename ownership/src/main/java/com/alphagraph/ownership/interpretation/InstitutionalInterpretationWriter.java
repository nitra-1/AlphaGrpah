package com.alphagraph.ownership.interpretation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.UUID;

/**
 * Upserts one {@code (symbol, as_of_date)} row - unlike {@code deal_materiality}'s append-only
 * design, this is a "latest interpretation per symbol per day" table, so a re-run for the same day
 * refreshes it in place. Reasons are deleted and reinserted every time, matching the parent's
 * upsert semantics.
 */
@Component
class InstitutionalInterpretationWriter {

    private final JdbcTemplate jdbcTemplate;

    InstitutionalInterpretationWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void write(InstitutionalInterpretationResult result) {
        UUID interpretationId = jdbcTemplate.query(
            """
            INSERT INTO ownership.institutional_interpretations (
                id, symbol, as_of_date, event_structure, institutional_state, discovery_confirmation_state,
                confirmation_frozen, event_anchor_date, confirmation_sessions_elapsed, confirmation_score,
                price_confirmation_score, delivery_confirmation_score, volume_confirmation_score,
                repeat_activity_confirmation_score, confirmation_coverage_pct, confidence, materiality_score,
                reported_flow_state, churn_state, institutional_buy_value, institutional_sell_value,
                institutional_buyer_count, institutional_seller_count, interpretation_readiness, rule_version, computed_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (symbol, as_of_date) DO UPDATE SET
                event_structure = EXCLUDED.event_structure, institutional_state = EXCLUDED.institutional_state,
                discovery_confirmation_state = EXCLUDED.discovery_confirmation_state,
                confirmation_frozen = EXCLUDED.confirmation_frozen, event_anchor_date = EXCLUDED.event_anchor_date,
                confirmation_sessions_elapsed = EXCLUDED.confirmation_sessions_elapsed,
                confirmation_score = EXCLUDED.confirmation_score, price_confirmation_score = EXCLUDED.price_confirmation_score,
                delivery_confirmation_score = EXCLUDED.delivery_confirmation_score,
                volume_confirmation_score = EXCLUDED.volume_confirmation_score,
                repeat_activity_confirmation_score = EXCLUDED.repeat_activity_confirmation_score,
                confirmation_coverage_pct = EXCLUDED.confirmation_coverage_pct, confidence = EXCLUDED.confidence,
                materiality_score = EXCLUDED.materiality_score, reported_flow_state = EXCLUDED.reported_flow_state,
                churn_state = EXCLUDED.churn_state, institutional_buy_value = EXCLUDED.institutional_buy_value,
                institutional_sell_value = EXCLUDED.institutional_sell_value,
                institutional_buyer_count = EXCLUDED.institutional_buyer_count,
                institutional_seller_count = EXCLUDED.institutional_seller_count,
                interpretation_readiness = EXCLUDED.interpretation_readiness,
                rule_version = EXCLUDED.rule_version, computed_at = EXCLUDED.computed_at
            RETURNING id
            """,
            (rs, rowNum) -> (UUID) rs.getObject("id"),
            UUID.randomUUID(), result.symbol(), Date.valueOf(result.asOfDate()), result.eventStructure().name(),
            result.institutionalState().name(), result.discoveryConfirmationState().name(), result.confirmationFrozen(),
            result.eventAnchorDate() == null ? null : Date.valueOf(result.eventAnchorDate()), result.confirmationSessionsElapsed(),
            result.confirmationScore(), result.priceConfirmationScore(), result.deliveryConfirmationScore(),
            result.volumeConfirmationScore(), result.repeatActivityConfirmationScore(), result.confirmationCoveragePct(),
            result.confidence(), result.materialityScore(), result.reportedFlowState(), result.churnState().name(),
            result.institutionalBuyValue(), result.institutionalSellValue(), result.institutionalBuyerCount(),
            result.institutionalSellerCount(), result.interpretationReadiness().name(), result.ruleVersion(),
            Timestamp.from(result.computedAt())
        ).get(0);

        jdbcTemplate.update("DELETE FROM ownership.institutional_interpretation_reasons WHERE interpretation_id = ?", interpretationId);
        for (ReasonCode reason : result.reasons()) {
            jdbcTemplate.update(
                "INSERT INTO ownership.institutional_interpretation_reasons (id, interpretation_id, reason_code, metric_value, evidence_reference) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), interpretationId, reason.code(), reason.metricValue(), reason.evidenceReference()
            );
        }
    }
}
