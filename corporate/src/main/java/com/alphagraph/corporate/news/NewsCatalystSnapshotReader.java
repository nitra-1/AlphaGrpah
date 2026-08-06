package com.alphagraph.corporate.news;

import com.alphagraph.corporate.api.NewsCatalystSnapshot;
import com.alphagraph.corporate.api.NewsCatalystTrend;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads an instrument's latest {@link NewsCatalystSnapshot} back - added in Module 2.8 (Corporate
 * Signal Engine), the first consumer to need one ({@link NewsCatalystSnapshotStore} was write-only
 * until now).
 */
@Component
public class NewsCatalystSnapshotReader {

    private final JdbcTemplate jdbcTemplate;

    public NewsCatalystSnapshotReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<NewsCatalystSnapshot> findLatest(UUID instrumentId) {
        List<NewsCatalystSnapshot> rows = jdbcTemplate.query(
            """
            SELECT instrument_id, symbol, as_of_date, catalyst_score, catalyst_trend,
                   recent_catalyst_count, confidence, rule_set_version, computed_at
            FROM corporate.news_catalyst_scores WHERE instrument_id = ? ORDER BY as_of_date DESC LIMIT 1
            """,
            (rs, rowNum) -> new NewsCatalystSnapshot(
                (UUID) rs.getObject("instrument_id"), rs.getString("symbol"), rs.getDate("as_of_date").toLocalDate(),
                rs.getDouble("catalyst_score"), NewsCatalystTrend.valueOf(rs.getString("catalyst_trend")),
                rs.getInt("recent_catalyst_count"), rs.getDouble("confidence"),
                rs.getInt("rule_set_version"), rs.getTimestamp("computed_at").toInstant()
            ),
            instrumentId
        );
        return rows.stream().findFirst();
    }
}
