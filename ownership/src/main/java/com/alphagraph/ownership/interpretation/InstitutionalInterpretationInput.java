package com.alphagraph.ownership.interpretation;

import java.time.LocalDate;
import java.util.List;

/** Everything {@link InstitutionalInterpretationEngine#assemble} needs, already computed by the orchestrator (event structure, confirmation) or read from Sprint 2 (materiality/flow). */
record InstitutionalInterpretationInput(
    String symbol, LocalDate asOfDate,
    SymbolFlowSummary flowSummary, EventStructure eventStructure, InstitutionalState institutionalState,
    MaterialityLevel materialityLevel, Double materialityScore, String reportedFlowState,
    List<AnchorCandidateDeal> windowDeals, DiscoveryConfirmationResult confirmation,
    List<DiscoveredPriceRow> preAnchorBaselineSessions, boolean allDealsInWindowScored, int ruleVersion
) {
}
