package com.alphagraph.corporate.commentary;

import com.alphagraph.corporate.api.GuidanceTrend;
import com.alphagraph.corporate.api.ManagementCommentarySnapshot;
import com.alphagraph.corporate.api.ManagementCredibility;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads an instrument's latest {@link ManagementCommentarySnapshot} back - added in Module 2.8
 * (Corporate Signal Engine), the first consumer to need one ({@link ManagementSnapshotStore} was
 * write-only until now).
 */
@Component
public class ManagementSnapshotReader {

    private final JdbcTemplate jdbcTemplate;

    public ManagementSnapshotReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ManagementCommentarySnapshot> findLatest(UUID instrumentId) {
        List<ManagementCommentarySnapshot> rows = jdbcTemplate.query(
            """
            SELECT instrument_id, symbol, as_of_date, growth_visibility_score, guidance_trend,
                   management_credibility, confidence, rule_set_version, computed_at
            FROM corporate.management_commentary_snapshots WHERE instrument_id = ? ORDER BY as_of_date DESC LIMIT 1
            """,
            (rs, rowNum) -> new ManagementCommentarySnapshot(
                (UUID) rs.getObject("instrument_id"), rs.getString("symbol"), rs.getDate("as_of_date").toLocalDate(),
                rs.getDouble("growth_visibility_score"), GuidanceTrend.valueOf(rs.getString("guidance_trend")),
                ManagementCredibility.valueOf(rs.getString("management_credibility")), rs.getDouble("confidence"),
                rs.getInt("rule_set_version"), rs.getTimestamp("computed_at").toInstant()
            ),
            instrumentId
        );
        return rows.stream().findFirst();
    }
}
