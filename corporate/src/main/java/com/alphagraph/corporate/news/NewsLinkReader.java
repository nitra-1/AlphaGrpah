package com.alphagraph.corporate.news;

import com.alphagraph.corporate.api.NewsImpactDirection;
import com.alphagraph.corporate.api.NewsInstrumentLink;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads catalyst links - the input to {@link NewsCatalystEngine}. Public since Module 2.10
 * (Decision Intelligence API Layer): the "Positive News"/"Negative News" dashboard widgets need to
 * rank across the whole tracked universe, not one instrument at a time like every prior consumer.
 */
@Component
public class NewsLinkReader {

    static final String CONSUMER = "NEWS_CATALYST_ENGINE";

    private static final RowMapper<NewsInstrumentLink> ROW_MAPPER = (rs, rowNum) -> new NewsInstrumentLink(
        (UUID) rs.getObject("id"), (UUID) rs.getObject("document_id"), (UUID) rs.getObject("instrument_id"),
        rs.getString("symbol"), NewsImpactDirection.valueOf(rs.getString("direction")), rs.getString("signal"),
        rs.getString("impact_summary"), rs.getDouble("extraction_confidence"), rs.getTimestamp("announced_at").toInstant()
    );

    private static final String SELECT_COLUMNS = """
        id, document_id, instrument_id, symbol, direction, signal, impact_summary, extraction_confidence, announced_at
        """;

    private final JdbcTemplate jdbcTemplate;

    NewsLinkReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<UUID> findDistinctInstrumentIds() {
        return jdbcTemplate.query(
            "SELECT DISTINCT instrument_id FROM corporate.document_instrument_links",
            (rs, rowNum) -> (UUID) rs.getObject("instrument_id")
        );
    }

    List<NewsInstrumentLink> findByInstrument(UUID instrumentId) {
        return jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " FROM corporate.document_instrument_links WHERE instrument_id = ? ORDER BY announced_at DESC",
            ROW_MAPPER, instrumentId
        );
    }

    /** Every link of the given direction across all instruments in the last {@code lookbackDays}, newest first. */
    public List<NewsInstrumentLink> findRecentByDirection(NewsImpactDirection direction, int lookbackDays) {
        Instant since = Instant.now().minusSeconds(lookbackDays * 86400L);
        return jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " FROM corporate.document_instrument_links WHERE direction = ? AND announced_at >= ? ORDER BY announced_at DESC",
            ROW_MAPPER, direction.name(), Timestamp.from(since)
        );
    }
}
