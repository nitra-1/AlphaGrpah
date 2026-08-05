package com.alphagraph.corporate.relationships;

import java.util.regex.Pattern;

/**
 * Shared normalization for matching a free-text name against a canonical name or alias -
 * lowercased, common corporate suffixes and punctuation stripped, whitespace collapsed. A real,
 * disclosed limitation: this is exact/substring matching, not fuzzy or embedding-based - "Tata
 * Consultancy Services" and "TCS" both normalize to a match, but a genuine misspelling or an
 * unfamiliar abbreviation won't. Extracted from {@code corporate.news.NewsInstrumentMatcher}
 * (Module 2.6) so {@link EntityResolver} and {@code NewsInstrumentMatcher} share one
 * implementation instead of two copies of the same regex.
 */
public final class EntityNameNormalizer {

    private static final Pattern CORPORATE_SUFFIXES = Pattern.compile(
        "\\b(limited|ltd|private|pvt|inc|incorporated|corp|corporation)\\b\\.?", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9 ]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private EntityNameNormalizer() {
    }

    public static String normalize(String raw) {
        String lower = raw.toLowerCase();
        String withoutSuffixes = CORPORATE_SUFFIXES.matcher(lower).replaceAll(" ");
        String alphanumericOnly = NON_ALPHANUMERIC.matcher(withoutSuffixes).replaceAll(" ");
        return WHITESPACE.matcher(alphanumericOnly).replaceAll(" ").trim();
    }

    /** Whether two already-normalized strings should be considered the same name. */
    public static boolean matches(String normalizedA, String normalizedB) {
        if (normalizedA.isBlank() || normalizedB.isBlank()) {
            return false;
        }
        return normalizedA.equals(normalizedB) || normalizedA.contains(normalizedB) || normalizedB.contains(normalizedA);
    }
}
