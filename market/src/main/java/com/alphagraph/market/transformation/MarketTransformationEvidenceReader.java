package com.alphagraph.market.transformation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads {@code market.transformation_evidence} back - Stage 1's own writer was write-only until
 * Stage 2 needed to read it. Stage 2 never recomputes change/velocity/persistence from raw prices
 * - {@link MarketAccumulationEngine} already computed and persisted them, so Stage 2 just reads
 * the single most recent row per (instrument, metric) and interprets it.
 */
@Component
class MarketTransformationEvidenceReader {

    private static final String SELECT_COLUMNS = """
        instrument_id, symbol, metric_name, trade_date, prior_trade_date, value, prior_value,
        change, velocity_per_day, persistence_days, confidence
        """;

    private final JdbcTemplate jdbcTemplate;

    MarketTransformationEvidenceReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    Optional<MarketEvidenceObservation> findLatest(UUID instrumentId, MarketMetric metric) {
        List<MarketEvidenceObservation> rows = jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " FROM market.transformation_evidence " +
            "WHERE instrument_id = ? AND metric_name = ? ORDER BY trade_date DESC LIMIT 1",
            (rs, rowNum) -> new MarketEvidenceObservation(
                MarketMetric.valueOf(rs.getString("metric_name")), (UUID) rs.getObject("instrument_id"), rs.getString("symbol"),
                rs.getDate("trade_date").toLocalDate(), rs.getDate("prior_trade_date") == null ? null : rs.getDate("prior_trade_date").toLocalDate(),
                rs.getBigDecimal("value"), rs.getBigDecimal("prior_value"), rs.getBigDecimal("change"), rs.getBigDecimal("velocity_per_day"),
                rs.getInt("persistence_days"), rs.getDouble("confidence")
            ),
            instrumentId, metric.name()
        );
        return rows.stream().findFirst();
    }

    /** Every instrument with at least one real evidence row - Stage 2's own driving universe, not market.daily_prices' raw universe. */
    List<UUID> findAllInstrumentIds() {
        return jdbcTemplate.query(
            "SELECT DISTINCT instrument_id FROM market.transformation_evidence",
            (rs, rowNum) -> (UUID) rs.getObject("instrument_id")
        );
    }
}
