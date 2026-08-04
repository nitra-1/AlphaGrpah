package com.alphagraph.corporate.news;

import com.alphagraph.corporate.api.NewsImpactDirection;
import com.alphagraph.corporate.api.NewsInstrumentLink;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Reads every catalyst link recorded for one instrument - the input to {@link NewsCatalystEngine}. */
@Component
class NewsLinkReader {

    static final String CONSUMER = "NEWS_CATALYST_ENGINE";

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
            """
            SELECT id, document_id, instrument_id, symbol, direction, signal, impact_summary, extraction_confidence, announced_at
            FROM corporate.document_instrument_links WHERE instrument_id = ? ORDER BY announced_at DESC
            """,
            (rs, rowNum) -> new NewsInstrumentLink(
                (UUID) rs.getObject("id"), (UUID) rs.getObject("document_id"), (UUID) rs.getObject("instrument_id"),
                rs.getString("symbol"), NewsImpactDirection.valueOf(rs.getString("direction")), rs.getString("signal"),
                rs.getString("impact_summary"), rs.getDouble("extraction_confidence"), rs.getTimestamp("announced_at").toInstant()
            ),
            instrumentId
        );
    }
}
