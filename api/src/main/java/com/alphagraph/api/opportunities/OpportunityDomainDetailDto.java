package com.alphagraph.api.opportunities;

import java.util.List;

/**
 * Mirrors {@code decision.api.OpportunityDomainDetail} exactly - one domain's full causal chain
 * (Stage 1 evidence -> Stage 2 inflection -> Stage 3 sequences -> Stage 4 contribution), anchored
 * to the same {@code asOfDate} as the containing {@link OpportunityDetailDto}'s own convergence
 * snapshot. Always one entry per domain (`FINANCIAL, OWNERSHIP, MARKET, SECTOR,
 * CAPITAL_ALLOCATION`), never omitted even when empty.
 */
public record OpportunityDomainDetailDto(
    String domain,
    List<EvidenceObservationDto> evidence,
    InflectionStateDto inflection,
    List<TransformationSequenceDto> sequences,
    DomainContributionDto convergenceContribution
) {
}
