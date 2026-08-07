package com.alphagraph.corporate.newsfeed;

import com.alphagraph.common.etl.Loader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Upserts a news article as a {@code corporate.documents} row - {@code instrument_id}/
 * {@code symbol} NULL (Module 2.6's whole premise: which companies this affects is determined
 * later, not at collection). No download/OCR stage exists for RSS-sourced text (see
 * {@code corporate.newsfeed} package-info), so this goes straight past PENDING/DOWNLOADED to
 * either PROCESSED (ready for tonight's automatic extraction) or PENDING_REVIEW (held for an
 * admin decision), decided by {@link NewsRelevanceFilter} - see that class and
 * {@code corporate.api.DocumentStatus} for the full rationale.
 *
 * <p>Two dedup layers: (1) {@code (source, external_id)} - the same outlet republishing the same
 * link is a natural no-op, same as every prior Loader; (2) a real, disclosed, deliberately simple
 * cross-outlet check - the same story covered by two different outlets within 48 hours is
 * detected by an exact match on the article title with all punctuation/whitespace/case stripped.
 * This is NOT semantic/embedding-based dedup (two outlets rarely use byte-identical headlines for
 * genuinely the same story) - a real limitation, deferred rather than silently worked around;
 * true near-duplicate detection would need a new pipeline stage using
 * {@code corporate.document_chunks}' existing embedding infrastructure, which nothing currently
 * populates for this fast (non-chunked) path.
 */
@Component
public class NewsFeedLoader implements Loader<NewsArticleDocument> {

    private static final Logger log = LoggerFactory.getLogger(NewsFeedLoader.class);
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]");
    private static final long DEDUP_WINDOW_HOURS = 48;

    private final JdbcTemplate jdbcTemplate;
    private final NewsRelevanceFilter relevanceFilter;

    public NewsFeedLoader(JdbcTemplate jdbcTemplate, NewsRelevanceFilter relevanceFilter) {
        this.jdbcTemplate = jdbcTemplate;
        this.relevanceFilter = relevanceFilter;
    }

    @Override
    public void load(NewsArticleDocument document) {
        if (isRecentDuplicateTitle(document.title(), document.announcedAt())) {
            log.debug("Skipping likely duplicate news title within {}h window: {}", DEDUP_WINDOW_HOURS, document.title());
            return;
        }

        String status = relevanceFilter.isRelevant(document.extractedText()) ? "PROCESSED" : "PENDING_REVIEW";

        List<UUID> inserted = jdbcTemplate.query(
            """
            INSERT INTO corporate.documents
                (id, instrument_id, symbol, source, external_id, category, title, source_url, announced_at, extracted_text, status)
            VALUES (?, NULL, NULL, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (source, external_id) DO NOTHING
            RETURNING id
            """,
            (rs, rowNum) -> (UUID) rs.getObject("id"),
            UUID.randomUUID(), document.source().name(), document.externalId(), document.category(),
            document.title(), document.sourceUrl(), Timestamp.from(document.announcedAt()), document.extractedText(), status
        );

        if (inserted.isEmpty()) {
            log.debug("News article already collected, skipping: source={} externalId={}", document.source(), document.externalId());
        } else if ("PENDING_REVIEW".equals(status)) {
            log.debug("News article filtered out (no tracked-instrument match), held for admin review: {}", document.title());
        }
    }

    private boolean isRecentDuplicateTitle(String title, Instant announcedAt) {
        String normalizedTitle = normalize(title);
        if (normalizedTitle.isBlank()) {
            return false;
        }
        Instant windowStart = announcedAt.minus(DEDUP_WINDOW_HOURS, ChronoUnit.HOURS);

        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM corporate.documents
            WHERE source = 'NEWS' AND announced_at >= ?
              AND lower(regexp_replace(title, '[^a-zA-Z0-9]+', '', 'g')) = ?
            """,
            Integer.class, Timestamp.from(windowStart), normalizedTitle
        );
        return count != null && count > 0;
    }

    private static String normalize(String raw) {
        return NON_ALPHANUMERIC.matcher(raw.toLowerCase()).replaceAll("");
    }
}
