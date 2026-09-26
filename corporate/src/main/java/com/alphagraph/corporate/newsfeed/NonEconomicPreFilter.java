package com.alphagraph.corporate.newsfeed;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Replaces {@link NewsRelevanceFilter} (News & Economic Discovery rework) as {@link
 * NewsFeedLoader}'s own gate - the old filter only matched *tracked-instrument* names, which
 * structurally can't recognize genuinely relevant economic/policy/sector news that doesn't name
 * an already-tracked company (most of what actually matters for discovering *new* opportunities).
 * This filter does the opposite job: a cheap, deterministic, **inclusion-biased** blocklist that
 * only rejects text whose vocabulary is dominated by clearly non-economic topics
 * (sports/entertainment/celebrity/crime) - everything else, including genuinely ambiguous
 * content, flows through to the real classification, which is {@code
 * corporate.knowledge.NewsExtractor}'s own {@code economicRelevance} field (an LLM call, the
 * authoritative classification). This filter exists purely to skip spending that call on content
 * with zero plausible economic angle - not to replace the real classification.
 *
 * <p>Deliberately keyword-based, not embeddings or a second LLM call - same "near-free next to
 * the Claude calls it's guarding" discipline {@link NewsRelevanceFilter} itself already
 * established. Biased toward inclusion: a single keyword hit is not enough to reject an article -
 * requires the blocklist vocabulary to make up a meaningful share of the article's own words, so a
 * genuine economic story that merely mentions a cricket sponsorship deal in passing still flows
 * through.
 */
@Component
class NonEconomicPreFilter {

    private static final Pattern WORD = Pattern.compile("[a-z]+");

    private static final List<String> BLOCKLIST_TERMS = List.of(
        // sports
        "cricket", "football", "soccer", "tennis", "badminton", "hockey", "olympics", "ipl",
        "wicket", "tournament", "championship", "athlete", "match", "stadium",
        // entertainment / celebrity
        "bollywood", "hollywood", "actor", "actress", "celebrity", "movie", "film", "song",
        "album", "concert", "wedding", "divorce", "reality show",
        // crime / accidents (non-economic unless it's a corporate/regulatory story, which will
        // carry enough other vocabulary to stay below the threshold)
        "murder", "arrested", "accident", "crash", "shooting", "robbery", "kidnap"
    );

    /** Minimum fraction of an article's own words that must hit the blocklist before it's rejected - keeps a passing mention from sinking a genuinely economic story. */
    private static final double REJECTION_THRESHOLD = 0.08;

    /** True if the text is plausibly economically relevant and should flow to real LLM classification - false only for content dominated by non-economic vocabulary. */
    boolean isPlausiblyEconomic(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase();
        List<String> words = WORD.matcher(lower).results().map(m -> m.group()).toList();
        if (words.isEmpty()) {
            return false;
        }

        long blocklistHits = words.stream().filter(BLOCKLIST_TERMS::contains).count();
        double blocklistShare = (double) blocklistHits / words.size();
        return blocklistShare < REJECTION_THRESHOLD;
    }
}
