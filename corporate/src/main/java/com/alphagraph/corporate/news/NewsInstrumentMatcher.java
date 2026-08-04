package com.alphagraph.corporate.news;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

record MatchedInstrument(UUID id, String symbol) {
}

/**
 * Resolves a free-text company name (as {@code corporate.knowledge.NewsExtractor} wrote it, in
 * its own words from the article text) against {@code reference.instruments} - the concrete
 * implementation of the "tracked instruments only" scoping decision. A real, disclosed
 * limitation: this is exact/substring matching after stripping common corporate suffixes and
 * punctuation, not fuzzy or embedding-based matching - "Tata Consultancy Services" and "TCS" both
 * resolve to the same instrument, but a genuine misspelling or an unfamiliar abbreviation won't.
 * Same class of simplification as {@code corporate.orderbook.OrderBookSignalDetector}'s
 * REPEAT_CUSTOMER name matching.
 */
@Component
class NewsInstrumentMatcher {

    private static final Pattern CORPORATE_SUFFIXES = Pattern.compile(
        "\\b(limited|ltd|private|pvt|inc|incorporated|corp|corporation)\\b\\.?", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9 ]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final JdbcTemplate jdbcTemplate;

    NewsInstrumentMatcher(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    Optional<MatchedInstrument> resolve(String companyName) {
        String normalizedInput = normalize(companyName);
        if (normalizedInput.isBlank()) {
            return Optional.empty();
        }

        List<Map<String, Object>> instruments = jdbcTemplate.queryForList(
            "SELECT id, symbol, name FROM reference.instruments"
        );

        for (Map<String, Object> row : instruments) {
            String symbol = (String) row.get("symbol");
            String normalizedName = normalize((String) row.get("name"));
            String normalizedSymbol = symbol.toLowerCase();

            boolean nameMatches = !normalizedName.isBlank()
                && (normalizedInput.equals(normalizedName) || normalizedInput.contains(normalizedName) || normalizedName.contains(normalizedInput));
            boolean symbolMatches = containsWholeWord(normalizedInput, normalizedSymbol);

            if (nameMatches || symbolMatches) {
                return Optional.of(new MatchedInstrument((UUID) row.get("id"), symbol));
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

    private static String normalize(String raw) {
        String lower = raw.toLowerCase();
        String withoutSuffixes = CORPORATE_SUFFIXES.matcher(lower).replaceAll(" ");
        String alphanumericOnly = NON_ALPHANUMERIC.matcher(withoutSuffixes).replaceAll(" ");
        return WHITESPACE.matcher(alphanumericOnly).replaceAll(" ").trim();
    }
}
