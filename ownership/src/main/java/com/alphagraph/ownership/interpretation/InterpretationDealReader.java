package com.alphagraph.ownership.interpretation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Raw {@code discovered_deals} rows for one symbol/date-range, joined to the resolved participant
 * (requires {@code participant_id} already resolved - by the time this runs,
 * {@link InstitutionalInterpretationOrchestrator}'s own self-healing pass has already resolved
 * every row) and, if scored yet, its Sprint 2 materiality level (unscored deals default to LOW,
 * never guessed higher - see {@code ownership.deals.DealMaterialityScoringOrchestrator}, which
 * skips a deal until its symbol has 20 real trading sessions of price history).
 *
 * <p>Excludes {@code duplicate_of_deal_id IS NOT NULL} rows - real evidence caught live: NSE's
 * bulk and block feeds can both independently report the exact same trade, and without this
 * exclusion a genuinely single real trade gets double-counted in every downstream aggregate this
 * feeds ({@link ParticipantFlowAnalyzer}, {@link DealEventStructureEngine}, the confirmation
 * engine's weighted event price) - see V11's migration comment.
 */
@Component
class InterpretationDealReader {

    private final JdbcTemplate jdbcTemplate;

    InterpretationDealReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<WindowDealRow> findWindowDeals(String symbol, LocalDate fromInclusive, LocalDate toInclusive) {
        return jdbcTemplate.query(
            """
            SELECT d.participant_id, p.canonical_name, p.participant_type, p.classification_confidence,
                   d.deal_date, d.buy_sell, d.quantity, d.price, d.deal_value,
                   m.materiality_level, m.materiality_score, m.reported_flow_state
            FROM ownership.discovered_deals d
            JOIN ownership.deal_participants p ON p.id = d.participant_id
            LEFT JOIN ownership.deal_materiality m ON m.discovered_deal_id = d.id
            WHERE d.symbol = ? AND d.deal_date BETWEEN ? AND ? AND d.duplicate_of_deal_id IS NULL
            """,
            (rs, rowNum) -> new WindowDealRow(
                (UUID) rs.getObject("participant_id"), rs.getString("canonical_name"),
                ParticipantType.valueOf(rs.getString("participant_type")), rs.getDouble("classification_confidence"),
                rs.getDate("deal_date").toLocalDate(), rs.getString("buy_sell"), rs.getBigDecimal("quantity"),
                rs.getBigDecimal("price"), rs.getBigDecimal("deal_value"), MaterialityLevel.fromString(rs.getString("materiality_level")),
                toDouble(rs.getBigDecimal("materiality_score")), rs.getString("reported_flow_state")
            ),
            symbol, Date.valueOf(fromInclusive), Date.valueOf(toInclusive)
        );
    }

    private static Double toDouble(java.math.BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
