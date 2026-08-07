package com.alphagraph.corporate.newsfeed;

import com.alphagraph.corporate.api.NewsReviewItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Reads news articles awaiting admin review - see {@link NewsRelevanceFilter} / {@link NewsFeedLoader} for how they land here. */
@Component
public class NewsReviewReader {

    private final JdbcTemplate jdbcTemplate;

    public NewsReviewReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<NewsReviewItem> findPendingReview() {
        return jdbcTemplate.query(
            """
            SELECT id, title, source, source_url, announced_at, extracted_text
            FROM corporate.documents WHERE status = 'PENDING_REVIEW' ORDER BY announced_at DESC
            """,
            (rs, rowNum) -> new NewsReviewItem(
                (UUID) rs.getObject("id"), rs.getString("title"), rs.getString("source"), rs.getString("source_url"),
                rs.getTimestamp("announced_at").toInstant(), rs.getString("extracted_text")
            )
        );
    }
}
