package com.alphagraph.corporate.signal;

import com.alphagraph.corporate.api.CorporateRating;
import com.alphagraph.corporate.api.CorporateScore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads an instrument's {@link CorporateScore}s back - the first reader in this package
 * ({@link CorporateScoreStore} was write-only until Module 2.9, AI Analyst). {@link #findHistory}
 * is what lets "BEL improved today" be verified deterministically (today's score genuinely higher
 * than a prior day's) rather than asserted by the LLM.
 */
@Component
public class CorporateScoreReader {

    private static final RowMapper<CorporateScore> ROW_MAPPER = (rs, rowNum) -> new CorporateScore(
        (UUID) rs.getObject("instrument_id"), rs.getString("symbol"), rs.getDate("as_of_date").toLocalDate(),
        rs.getDouble("corporate_score"), CorporateRating.valueOf(rs.getString("corporate_rating")),
        rs.getObject("order_book_score") == null ? null : rs.getDouble("order_book_score"),
        rs.getObject("management_score") == null ? null : rs.getDouble("management_score"),
        rs.getObject("news_catalyst_score") == null ? null : rs.getDouble("news_catalyst_score"),
        rs.getInt("event_net_signal"), rs.getDouble("confidence"), rs.getInt("rule_set_version"),
        rs.getTimestamp("computed_at").toInstant()
    );

    private final JdbcTemplate jdbcTemplate;

    public CorporateScoreReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<CorporateScore> findLatest(UUID instrumentId) {
        List<CorporateScore> rows = jdbcTemplate.query(
            """
            SELECT instrument_id, symbol, as_of_date, corporate_score, corporate_rating, order_book_score,
                   management_score, news_catalyst_score, event_net_signal, confidence, rule_set_version, computed_at
            FROM corporate.corporate_scores WHERE instrument_id = ? ORDER BY as_of_date DESC LIMIT 1
            """,
            ROW_MAPPER, instrumentId
        );
        return rows.stream().findFirst();
    }

    /** Every score on record for one instrument, newest first. */
    public List<CorporateScore> findHistory(UUID instrumentId) {
        return jdbcTemplate.query(
            """
            SELECT instrument_id, symbol, as_of_date, corporate_score, corporate_rating, order_book_score,
                   management_score, news_catalyst_score, event_net_signal, confidence, rule_set_version, computed_at
            FROM corporate.corporate_scores WHERE instrument_id = ? ORDER BY as_of_date DESC
            """,
            ROW_MAPPER, instrumentId
        );
    }
}
