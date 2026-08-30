package com.alphagraph.ownership.interpretation;

import com.alphagraph.ownership.api.InstitutionalInterpretationDetail;
import com.alphagraph.ownership.api.InterpretationReason;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The latest interpretation + its reason codes for one symbol - for the Discovery page's "Why?" expandable section. */
@Component
public class InterpretationDetailReader {

    private final JdbcTemplate jdbcTemplate;

    public InterpretationDetailReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<InstitutionalInterpretationDetail> findLatest(String symbol) {
        List<Row> rows = jdbcTemplate.query(
            """
            SELECT id, symbol, as_of_date, event_structure, institutional_state, discovery_confirmation_state,
                   confirmation_frozen, event_anchor_date, confirmation_sessions_elapsed, confirmation_score,
                   price_confirmation_score, delivery_confirmation_score, volume_confirmation_score,
                   repeat_activity_confirmation_score, confirmation_coverage_pct, confidence, materiality_score,
                   reported_flow_state, churn_state, institutional_buy_value, institutional_sell_value,
                   institutional_buyer_count, institutional_seller_count, interpretation_readiness
            FROM ownership.institutional_interpretations
            WHERE symbol = ?
            ORDER BY as_of_date DESC
            LIMIT 1
            """,
            (rs, rowNum) -> new Row(
                (UUID) rs.getObject("id"), rs.getString("symbol"), rs.getDate("as_of_date").toLocalDate(),
                rs.getString("event_structure"), rs.getString("institutional_state"), rs.getString("discovery_confirmation_state"),
                rs.getBoolean("confirmation_frozen"),
                rs.getDate("event_anchor_date") == null ? null : rs.getDate("event_anchor_date").toLocalDate(),
                rs.getInt("confirmation_sessions_elapsed"), rs.getBigDecimal("confirmation_score"),
                rs.getBigDecimal("price_confirmation_score"), rs.getBigDecimal("delivery_confirmation_score"),
                rs.getBigDecimal("volume_confirmation_score"), rs.getBigDecimal("repeat_activity_confirmation_score"),
                rs.getBigDecimal("confirmation_coverage_pct"), rs.getDouble("confidence"), rs.getBigDecimal("materiality_score"),
                rs.getString("reported_flow_state"), rs.getString("churn_state"), rs.getBigDecimal("institutional_buy_value"),
                rs.getBigDecimal("institutional_sell_value"), rs.getInt("institutional_buyer_count"), rs.getInt("institutional_seller_count"),
                rs.getString("interpretation_readiness")
            ),
            symbol
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }

        Row row = rows.get(0);
        List<InterpretationReason> reasons = jdbcTemplate.query(
            "SELECT reason_code, metric_value, evidence_reference FROM ownership.institutional_interpretation_reasons WHERE interpretation_id = ?",
            (rs, rowNum) -> new InterpretationReason(
                rs.getString("reason_code"), toDouble(rs.getBigDecimal("metric_value")), rs.getString("evidence_reference")
            ),
            row.id
        );

        return Optional.of(new InstitutionalInterpretationDetail(
            row.symbol, row.asOfDate, row.eventStructure, row.institutionalState, row.discoveryConfirmationState,
            row.confirmationFrozen, row.eventAnchorDate, row.confirmationSessionsElapsed, row.confirmationScore,
            row.priceConfirmationScore, row.deliveryConfirmationScore, row.volumeConfirmationScore,
            row.repeatActivityConfirmationScore, row.confirmationCoveragePct, row.confidence, toDouble(row.materialityScore),
            row.reportedFlowState, row.churnState, row.institutionalBuyValue, row.institutionalSellValue,
            row.institutionalBuyerCount, row.institutionalSellerCount, row.interpretationReadiness, reasons
        ));
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private record Row(
        UUID id, String symbol, LocalDate asOfDate, String eventStructure, String institutionalState,
        String discoveryConfirmationState, boolean confirmationFrozen, LocalDate eventAnchorDate,
        int confirmationSessionsElapsed, BigDecimal confirmationScore, BigDecimal priceConfirmationScore,
        BigDecimal deliveryConfirmationScore, BigDecimal volumeConfirmationScore, BigDecimal repeatActivityConfirmationScore,
        BigDecimal confirmationCoveragePct, double confidence, BigDecimal materialityScore, String reportedFlowState,
        String churnState, BigDecimal institutionalBuyValue, BigDecimal institutionalSellValue,
        int institutionalBuyerCount, int institutionalSellerCount, String interpretationReadiness
    ) {
    }
}
