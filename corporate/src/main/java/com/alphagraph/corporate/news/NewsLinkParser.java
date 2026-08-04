package com.alphagraph.corporate.news;

import com.alphagraph.corporate.api.DocumentFact;
import com.alphagraph.corporate.api.NewsImpactDirection;
import com.alphagraph.corporate.api.NewsInstrumentLink;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reassembles {@code corporate.knowledge.NewsExtractor}'s facts (grouped by {@code fact_group},
 * since one news item can name several companies) into {@link NewsInstrumentLink}s. A group
 * missing companyName/direction is skipped, same as {@code corporate.commentary.
 * ManagementObservationParser}; a group whose companyName doesn't resolve to a tracked instrument
 * (via {@link NewsInstrumentMatcher}) is ALSO skipped - most real news mentions companies
 * AlphaGraph doesn't track yet, and that's a real, expected outcome, not an error.
 */
@Component
class NewsLinkParser {

    private final NewsInstrumentMatcher matcher;

    NewsLinkParser(NewsInstrumentMatcher matcher) {
        this.matcher = matcher;
    }

    List<NewsInstrumentLink> parse(UUID documentId, Instant announcedAt, List<DocumentFact> facts) {
        Map<UUID, List<DocumentFact>> byGroup = new LinkedHashMap<>();
        for (DocumentFact fact : facts) {
            if (fact.factGroup() == null) {
                continue;
            }
            byGroup.computeIfAbsent(fact.factGroup(), g -> new ArrayList<>()).add(fact);
        }

        List<NewsInstrumentLink> links = new ArrayList<>();
        for (List<DocumentFact> group : byGroup.values()) {
            parseOne(documentId, announcedAt, group).ifPresent(links::add);
        }
        return links;
    }

    private Optional<NewsInstrumentLink> parseOne(UUID documentId, Instant announcedAt, List<DocumentFact> group) {
        Map<String, DocumentFact> byType = new LinkedHashMap<>();
        for (DocumentFact fact : group) {
            byType.put(fact.factType(), fact);
        }

        DocumentFact companyFact = byType.get("companyname");
        DocumentFact directionFact = byType.get("direction");
        if (companyFact == null || directionFact == null) {
            return Optional.empty();
        }

        NewsImpactDirection direction;
        try {
            direction = NewsImpactDirection.valueOf(directionFact.factValue().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        Optional<MatchedInstrument> matched = matcher.resolve(companyFact.factValue());
        if (matched.isEmpty()) {
            return Optional.empty();
        }

        DocumentFact signalFact = byType.get("signal");
        DocumentFact impactSummaryFact = byType.get("impactsummary");

        return Optional.of(new NewsInstrumentLink(
            UUID.randomUUID(), documentId, matched.get().id(), matched.get().symbol(), direction,
            signalFact == null ? "" : signalFact.factValue(),
            impactSummaryFact == null ? "" : impactSummaryFact.factValue(),
            companyFact.confidence(), announcedAt
        ));
    }
}
