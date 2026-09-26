package com.alphagraph.corporate.knowledge;

import java.util.List;

/**
 * Root shape, extended (News & Economic Discovery rework) with the event-level classification
 * ({@code economicRelevance/theme/eventDirection/magnitude/eventConfidence/horizon}) and
 * {@code sectorImpacts} alongside the original per-company {@code impacts} - one LLM call still
 * produces both, keeping cost flat versus the original per-document call.
 */
record LlmNewsExtractionResponse(
    String economicRelevance, String theme, String eventDirection, String magnitude, int eventConfidence, String horizon,
    List<LlmSectorImpact> sectorImpacts, List<LlmNewsCompanyImpact> impacts
) {
}

/** One sector's direction/strength for this event - deliberately many-per-event, never collapsed to one sector-level verdict (e.g. crude oil up: Airlines NEGATIVE, Oil Producers POSITIVE, both real rows). */
record LlmSectorImpact(String sector, String direction, String strength, int confidence, String mechanism) {
}

record LlmNewsCompanyImpact(
    String companyName, String direction, String signal, String impactSummary, int confidence,
    String sector, String exposureType, String impactStrength,
    String relatedEntityName, String relatedEntityType, String relationshipType
) {
}
