package com.alphagraph.corporate.news;

import com.alphagraph.corporate.api.DocumentFact;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reassembles {@code corporate.knowledge.NewsExtractor}'s extended fact output into one {@link
 * ParsedNewsDocument} - the event-level group (identified by carrying {@code economicrelevance}),
 * every sector-impact group (identified by {@code sectorname}), and every company-impact group
 * (identified by {@code companyname}), same {@code fact_group}-based reassembly {@link
 * NewsLinkParser} already established for the original company-only shape.
 */
@Component
class EconomicEventParser {

    ParsedNewsDocument parse(UUID documentId, List<DocumentFact> facts) {
        Map<UUID, List<DocumentFact>> byGroup = new LinkedHashMap<>();
        for (DocumentFact fact : facts) {
            if (fact.factGroup() == null) {
                continue;
            }
            byGroup.computeIfAbsent(fact.factGroup(), g -> new ArrayList<>()).add(fact);
        }

        ParsedEconomicEvent event = null;
        List<ParsedSectorImpact> sectorImpacts = new ArrayList<>();
        List<ParsedCompanyImpact> companyImpacts = new ArrayList<>();

        for (List<DocumentFact> group : byGroup.values()) {
            Map<String, DocumentFact> byType = new LinkedHashMap<>();
            for (DocumentFact fact : group) {
                byType.put(fact.factType(), fact);
            }

            if (byType.containsKey("economicrelevance")) {
                event = parseEvent(byType);
            } else if (byType.containsKey("sectorname")) {
                parseSectorImpact(byType).ifPresent(sectorImpacts::add);
            } else if (byType.containsKey("companyname")) {
                parseCompanyImpact(byType).ifPresent(companyImpacts::add);
            }
        }

        return new ParsedNewsDocument(documentId, event, sectorImpacts, companyImpacts);
    }

    private static ParsedEconomicEvent parseEvent(Map<String, DocumentFact> byType) {
        DocumentFact relevanceFact = byType.get("economicrelevance");
        return new ParsedEconomicEvent(
            relevanceFact.factValue(), value(byType, "theme"), value(byType, "eventdirection"),
            value(byType, "magnitude"), relevanceFact.confidence(), value(byType, "horizon")
        );
    }

    private static java.util.Optional<ParsedSectorImpact> parseSectorImpact(Map<String, DocumentFact> byType) {
        DocumentFact sectorFact = byType.get("sectorname");
        DocumentFact directionFact = byType.get("sectordirection");
        DocumentFact strengthFact = byType.get("sectorstrength");
        if (directionFact == null || strengthFact == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new ParsedSectorImpact(
            sectorFact.factValue(), directionFact.factValue(), strengthFact.factValue(),
            sectorFact.confidence(), value(byType, "mechanism")
        ));
    }

    private static java.util.Optional<ParsedCompanyImpact> parseCompanyImpact(Map<String, DocumentFact> byType) {
        DocumentFact companyFact = byType.get("companyname");
        DocumentFact directionFact = byType.get("direction");
        if (directionFact == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new ParsedCompanyImpact(
            companyFact.factValue(), directionFact.factValue(), value(byType, "signal"), value(byType, "impactsummary"),
            companyFact.confidence(), value(byType, "sector"), value(byType, "exposuretype"), value(byType, "impactstrength")
        ));
    }

    private static String value(Map<String, DocumentFact> byType, String key) {
        DocumentFact fact = byType.get(key);
        return fact == null ? null : fact.factValue();
    }
}
