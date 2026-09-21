package com.alphagraph.ownership.transformation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Reads {@code ownership.shareholding_pattern} including the four XBRL sub-category columns -
 * structurally mirrors {@code ownership.engine.ShareholdingReader}, kept as an independent class
 * rather than modifying that one (which only ever needs the original 5 columns and must keep
 * working unchanged for {@code InstitutionalEngine}).
 */
@Component
class TransformationHistoryReader {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final JdbcTemplate jdbcTemplate;

    TransformationHistoryReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<TransformationShareholdingPeriod> findPeriods(UUID instrumentId) {
        return jdbcTemplate.query(
            """
            SELECT sp.instrument_id, i.symbol, sp.period_end, sp.created_at, sp.xbrl_enriched_at,
                   sp.promoter_percentage, sp.fii_percentage,
                   sp.dii_percentage, sp.mf_percentage, sp.public_percentage, sp.fpi_category_1_percentage,
                   sp.fpi_category_2_percentage, sp.insurance_companies_percentage, sp.other_financial_institutions_percentage
            FROM ownership.shareholding_pattern sp
            JOIN reference.instruments i ON i.id = sp.instrument_id
            WHERE sp.instrument_id = ?
            ORDER BY sp.period_end ASC
            """,
            (rs, rowNum) -> new TransformationShareholdingPeriod(
                (UUID) rs.getObject("instrument_id"), rs.getString("symbol"), rs.getDate("period_end").toLocalDate(),
                toIstDate(rs.getTimestamp("created_at")), toIstDate(rs.getTimestamp("xbrl_enriched_at")),
                rs.getBigDecimal("promoter_percentage"), rs.getBigDecimal("fii_percentage"), rs.getBigDecimal("dii_percentage"),
                rs.getBigDecimal("mf_percentage"), rs.getBigDecimal("public_percentage"), rs.getBigDecimal("fpi_category_1_percentage"),
                rs.getBigDecimal("fpi_category_2_percentage"), rs.getBigDecimal("insurance_companies_percentage"),
                rs.getBigDecimal("other_financial_institutions_percentage")
            ),
            instrumentId
        );
    }

    /** {@code timestamptz -> Instant -> LocalDate} via the real app zone, never the JVM's ambient default (same convention {@code InstitutionalScoreReader}/{@code RiskScoreReader} already use for the {@code Instant} half of this conversion). */
    private static LocalDate toIstDate(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().atZone(IST).toLocalDate();
    }
}
