package com.alphagraph.ownership.engine;

import com.alphagraph.ownership.api.DeliveryStatus;
import com.alphagraph.ownership.api.InstitutionalBehaviour;
import com.alphagraph.ownership.api.InstitutionalFlowStatus;
import com.alphagraph.ownership.api.InstitutionalScore;
import com.alphagraph.ownership.api.PromoterStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads ownership.institutional_scores directly - it's this module's own table. Added in Module
 * 1.9 (Risk Engine): Module 1.7 only needed an {@link InstitutionalScoreWriter}, since nothing
 * had to read an institutional score back until the Risk Engine needed it as an input.
 */
@Component
public class InstitutionalScoreReader {

    private final JdbcTemplate jdbcTemplate;

    public InstitutionalScoreReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String SELECT_COLUMNS = """
        SELECT is2.instrument_id, i.symbol, is2.as_of_date, is2.promoter_status, is2.fii_status,
               is2.dii_status, is2.mf_status, is2.delivery_status, is2.institutional_behaviour,
               is2.institutional_score, is2.confidence, is2.promoter_percentage,
               is2.promoter_change_percentage, is2.fii_change_percentage, is2.dii_change_percentage,
               is2.mf_change_percentage, is2.avg_delivery_percentage, is2.relative_volume,
               is2.net_bulk_deal_quantity, is2.rule_set_version, is2.computed_at
        FROM ownership.institutional_scores is2
        JOIN reference.instruments i ON i.id = is2.instrument_id
        """;

    public Optional<InstitutionalScore> findLatest(UUID instrumentId) {
        List<InstitutionalScore> rows = jdbcTemplate.query(
            SELECT_COLUMNS + " WHERE is2.instrument_id = ? ORDER BY is2.as_of_date DESC LIMIT 1",
            this::mapRow,
            instrumentId
        );
        return rows.stream().findFirst();
    }

    /** Latest score with as_of_date on or before {@code asOfDate} - used to resolve what a decision made on that date could actually have known, rather than always pulling whatever's newest today. */
    public Optional<InstitutionalScore> findAsOf(UUID instrumentId, LocalDate asOfDate) {
        List<InstitutionalScore> rows = jdbcTemplate.query(
            SELECT_COLUMNS + " WHERE is2.instrument_id = ? AND is2.as_of_date <= ? ORDER BY is2.as_of_date DESC LIMIT 1",
            this::mapRow,
            instrumentId, Date.valueOf(asOfDate)
        );
        return rows.stream().findFirst();
    }

    private InstitutionalScore mapRow(ResultSet rs, int rowNum) throws SQLException {
        Long netBulkDealQuantity = (Long) rs.getObject("net_bulk_deal_quantity");
        return new InstitutionalScore(
            (UUID) rs.getObject("instrument_id"), rs.getString("symbol"), rs.getDate("as_of_date").toLocalDate(),
            PromoterStatus.valueOf(rs.getString("promoter_status")), InstitutionalFlowStatus.valueOf(rs.getString("fii_status")),
            InstitutionalFlowStatus.valueOf(rs.getString("dii_status")), InstitutionalFlowStatus.valueOf(rs.getString("mf_status")),
            DeliveryStatus.valueOf(rs.getString("delivery_status")), InstitutionalBehaviour.valueOf(rs.getString("institutional_behaviour")),
            rs.getDouble("institutional_score"), rs.getDouble("confidence"),
            toDouble(rs.getBigDecimal("promoter_percentage")), toDouble(rs.getBigDecimal("promoter_change_percentage")),
            toDouble(rs.getBigDecimal("fii_change_percentage")), toDouble(rs.getBigDecimal("dii_change_percentage")),
            toDouble(rs.getBigDecimal("mf_change_percentage")), toDouble(rs.getBigDecimal("avg_delivery_percentage")),
            toDouble(rs.getBigDecimal("relative_volume")), netBulkDealQuantity,
            rs.getInt("rule_set_version"), rs.getTimestamp("computed_at").toInstant()
        );
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
