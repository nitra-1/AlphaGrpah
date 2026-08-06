package com.alphagraph.corporate.news;

import com.alphagraph.corporate.api.NewsCatalystSnapshot;
import com.alphagraph.corporate.api.NewsCatalystTrend;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads {@link NewsCatalystSnapshot}s back - added in Module 2.8 (Corporate Signal Engine), the
 * first consumer to need one ({@link NewsCatalystSnapshotStore} was write-only until now).
 * {@link #findAllLatest} was added in Module 2.10 (Decision Intelligence API Layer) for the
 * "Top Catalysts" dashboard widget - ranking across every instrument, not reading one at a time.
 */
@Component
public class NewsCatalystSnapshotReader {

    private static final RowMapper<NewsCatalystSnapshot> ROW_MAPPER = (rs, rowNum) -> new NewsCatalystSnapshot(
        (UUID) rs.getObject("instrument_id"), rs.getString("symbol"), rs.getDate("as_of_date").toLocalDate(),
        rs.getDouble("catalyst_score"), NewsCatalystTrend.valueOf(rs.getString("catalyst_trend")),
        rs.getInt("recent_catalyst_count"), rs.getDouble("confidence"),
        rs.getInt("rule_set_version"), rs.getTimestamp("computed_at").toInstant()
    );

    private static final String SELECT_COLUMNS = """
        instrument_id, symbol, as_of_date, catalyst_score, catalyst_trend, recent_catalyst_count, confidence, rule_set_version, computed_at
        """;

    private final JdbcTemplate jdbcTemplate;

    public NewsCatalystSnapshotReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<NewsCatalystSnapshot> findLatest(UUID instrumentId) {
        List<NewsCatalystSnapshot> rows = jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " FROM corporate.news_catalyst_scores WHERE instrument_id = ? ORDER BY as_of_date DESC LIMIT 1",
            ROW_MAPPER, instrumentId
        );
        return rows.stream().findFirst();
    }

    /** Every instrument's latest snapshot, highest catalyst score first. */
    public List<NewsCatalystSnapshot> findAllLatest() {
        return jdbcTemplate.query(
            "SELECT DISTINCT ON (instrument_id) " + SELECT_COLUMNS +
            " FROM corporate.news_catalyst_scores ORDER BY instrument_id, as_of_date DESC",
            ROW_MAPPER
        ).stream().sorted((a, b) -> Double.compare(b.catalystScore(), a.catalystScore())).toList();
    }
}
