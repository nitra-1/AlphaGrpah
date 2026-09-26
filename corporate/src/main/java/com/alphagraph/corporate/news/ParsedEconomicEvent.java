package com.alphagraph.corporate.news;

import java.util.List;
import java.util.UUID;

/** One document's economic-event-level classification, reassembled from {@code corporate.knowledge.NewsExtractor}'s facts. */
record ParsedEconomicEvent(String economicRelevance, String theme, String direction, String magnitude, double confidence, String horizon) {
}

/** One sector's direction/strength for this event - many can exist per event, never collapsed to one verdict. */
record ParsedSectorImpact(String sector, String direction, String strength, double confidence, String mechanism) {
}

/** One company's exposure to this event, as the LLM named it - tracked/untracked resolution happens later, in {@link EconomicEventWriter}. */
record ParsedCompanyImpact(String companyName, String direction, String signal, String impactSummary, double confidence, String sector, String exposureType, String impactStrength) {
}

/** The full reassembled shape for one document - {@code event} is null only if the document's facts carried no event-level group at all (should not happen for a real extraction, but never assumed). */
record ParsedNewsDocument(UUID documentId, ParsedEconomicEvent event, List<ParsedSectorImpact> sectorImpacts, List<ParsedCompanyImpact> companyImpacts) {
}
