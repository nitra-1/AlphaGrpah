package com.alphagraph.ownership.engine;

import com.alphagraph.ownership.api.ShareholdingPattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Reads ownership.shareholding_pattern directly - it's this module's own table. */
@Component
public class ShareholdingReader {

    private final JdbcTemplate jdbcTemplate;

    public ShareholdingReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<UUID> instrumentIdsWithShareholdingData() {
        return jdbcTemplate.query(
            "SELECT DISTINCT instrument_id FROM ownership.shareholding_pattern",
            (rs, rowNum) -> (UUID) rs.getObject("instrument_id")
        );
    }

    public List<ShareholdingPattern> findPeriods(UUID instrumentId) {
        return jdbcTemplate.query(
            """
            SELECT sp.instrument_id, i.symbol, sp.period_end, sp.promoter_percentage, sp.fii_percentage,
                   sp.dii_percentage, sp.mf_percentage, sp.public_percentage
            FROM ownership.shareholding_pattern sp
            JOIN reference.instruments i ON i.id = sp.instrument_id
            WHERE sp.instrument_id = ?
            ORDER BY sp.period_end ASC
            """,
            (rs, rowNum) -> new ShareholdingPattern(
                (UUID) rs.getObject("instrument_id"), rs.getString("symbol"), rs.getDate("period_end").toLocalDate(),
                rs.getBigDecimal("promoter_percentage"), rs.getBigDecimal("fii_percentage"),
                rs.getBigDecimal("dii_percentage"), rs.getBigDecimal("mf_percentage"), rs.getBigDecimal("public_percentage")
            ),
            instrumentId
        );
    }
}
