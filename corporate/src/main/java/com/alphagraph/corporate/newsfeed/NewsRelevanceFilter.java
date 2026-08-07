package com.alphagraph.corporate.newsfeed;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Cheap, deterministic relevance check run before a collected news article is queued for (billed)
 * Claude extraction - {@link RssFeedCollector} pulls from general "Markets" feeds with no
 * per-company scoping, so most collected articles are never about any of the tracked instruments
 * at all. Matches article text against each tracked instrument's symbol and company name (legal
 * suffixes like "Ltd"/"Limited" stripped, since news prose almost never uses the full legal name),
 * whole-word and case-insensitive so a short symbol like ACE doesn't match inside an unrelated
 * word ("faced", "space", ...).
 *
 * <p>Deliberately simple substring/word matching, not embeddings or a second LLM call - the whole
 * point is to be near-free next to the Claude calls it's guarding. Known, disclosed limitation: a
 * real story that doesn't happen to name a company/symbol (e.g. "the IT major" without naming it)
 * is a false negative here - handled by routing non-matches to admin review instead of discarding
 * them outright (see {@link NewsFeedLoader}), not by trying to make keyword matching perfect.
 */
@Component
class NewsRelevanceFilter {

    private static final Pattern LEGAL_SUFFIX = Pattern.compile("\\b(Ltd|Limited)\\.?\\s*$", Pattern.CASE_INSENSITIVE);

    private final JdbcTemplate jdbcTemplate;

    NewsRelevanceFilter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** True if the given text mentions any tracked instrument by symbol or (suffix-stripped) name. */
    boolean isRelevant(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String haystack = text.toLowerCase();
        for (String alias : loadAliases()) {
            if (matchesWholeWord(haystack, alias)) {
                return true;
            }
        }
        return false;
    }

    private List<String> loadAliases() {
        List<String> aliases = new ArrayList<>(jdbcTemplate.queryForList("SELECT symbol FROM reference.instruments", String.class));
        for (String name : jdbcTemplate.queryForList("SELECT name FROM reference.instruments", String.class)) {
            aliases.add(LEGAL_SUFFIX.matcher(name).replaceAll("").trim());
        }
        return aliases;
    }

    private static boolean matchesWholeWord(String haystackLower, String alias) {
        if (alias == null || alias.isBlank()) {
            return false;
        }
        return Pattern.compile("\\b" + Pattern.quote(alias.toLowerCase()) + "\\b").matcher(haystackLower).find();
    }
}
