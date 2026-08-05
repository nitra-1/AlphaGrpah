package com.alphagraph.corporate.news;

import com.alphagraph.corporate.relationships.EntityNameNormalizer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Array;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

record MatchedInstrument(UUID id, String symbol) {
}

/**
 * Resolves a free-text company name (as {@code corporate.knowledge.NewsExtractor} wrote it, in
 * its own words from the article text) against {@code knowledge.entity_master} - narrowed to
 * COMPANY entities that are themselves tracked instruments ({@code linked_instrument_id IS NOT
 * NULL}), the concrete implementation of the "tracked instruments only" scoping decision (Module
 * 2.6). Unlike {@link com.alphagraph.corporate.relationships.EntityResolver}, this never creates a
 * new entity - an unmatched name (a genuinely untracked company) simply produces no result, not a
 * new row. Since Module 2.7's retrofit, {@code entity_master} is the single canonical source of
 * every tracked instrument's known names/aliases, so this is a read-only, non-creating view over
 * the same data {@code EntityResolver} resolves against - sharing
 * {@link EntityNameNormalizer} rather than keeping its own copy of the matching logic.
 */
@Component
class NewsInstrumentMatcher {

    private final JdbcTemplate jdbcTemplate;

    NewsInstrumentMatcher(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    Optional<MatchedInstrument> resolve(String companyName) {
        String normalizedInput = EntityNameNormalizer.normalize(companyName);
        if (normalizedInput.isBlank()) {
            return Optional.empty();
        }

        List<TrackedCompanyRow> rows = jdbcTemplate.query(
            """
            SELECT e.linked_instrument_id AS instrument_id, e.canonical_name AS symbol, e.aliases AS aliases
            FROM knowledge.entity_master e
            WHERE e.entity_type = 'COMPANY' AND e.linked_instrument_id IS NOT NULL
            """,
            (rs, rowNum) -> new TrackedCompanyRow(
                (UUID) rs.getObject("instrument_id"), rs.getString("symbol"), toStringArray(rs.getArray("aliases"))
            )
        );

        for (TrackedCompanyRow row : rows) {
            boolean symbolMatches = containsWholeWord(normalizedInput, row.symbol().toLowerCase());
            boolean aliasMatches = false;
            for (String alias : row.aliases()) {
                if (EntityNameNormalizer.matches(normalizedInput, EntityNameNormalizer.normalize(alias))) {
                    aliasMatches = true;
                    break;
                }
            }

            if (symbolMatches || aliasMatches) {
                return Optional.of(new MatchedInstrument(row.instrumentId(), row.symbol()));
            }
        }
        return Optional.empty();
    }

    private static boolean containsWholeWord(String haystack, String word) {
        for (String token : haystack.split(" ")) {
            if (token.equals(word)) {
                return true;
            }
        }
        return false;
    }

    private static String[] toStringArray(Array sqlArray) {
        if (sqlArray == null) {
            return new String[0];
        }
        try {
            return (String[]) sqlArray.getArray();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read aliases array from knowledge.entity_master", e);
        }
    }

    private record TrackedCompanyRow(UUID instrumentId, String symbol, String[] aliases) {
    }
}
