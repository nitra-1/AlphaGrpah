package com.alphagraph.decision.api;

import java.util.List;

/**
 * One domain's full explainability chain as of the displayed Stage 4 convergence snapshot's own
 * {@code asOfDate} - Stage 1 evidence -> Stage 2 inflection -> Stage 3 sequences -> this domain's
 * Stage 4 contribution. Always one entry per {@code domain} in {@code FINANCIAL, OWNERSHIP,
 * MARKET, SECTOR, CAPITAL_ALLOCATION} - matching {@code convergence_domain_contributions}' own
 * "always exactly the 5 positive domains" convention - with empty lists/null fields where a
 * domain genuinely has nothing as of that date, never omitted.
 */
public record OpportunityDomainDetail(
    String domain,
    List<EvidenceObservation> evidence,
    InflectionState inflection,
    List<TransformationSequence> sequences,
    DomainContribution convergenceContribution
) {
}
