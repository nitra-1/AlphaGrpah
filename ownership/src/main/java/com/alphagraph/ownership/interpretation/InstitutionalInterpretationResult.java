package com.alphagraph.ownership.interpretation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** {@link InstitutionalInterpretationEngine#assemble}'s output - one full row for {@code ownership.institutional_interpretations} plus its reason codes. */
record InstitutionalInterpretationResult(
    String symbol, LocalDate asOfDate, EventStructure eventStructure, InstitutionalState institutionalState,
    DiscoveryConfirmationState discoveryConfirmationState, boolean confirmationFrozen, LocalDate eventAnchorDate,
    int confirmationSessionsElapsed, BigDecimal confirmationScore, BigDecimal priceConfirmationScore,
    BigDecimal deliveryConfirmationScore, BigDecimal volumeConfirmationScore, BigDecimal repeatActivityConfirmationScore,
    BigDecimal confirmationCoveragePct, double confidence, Double materialityScore, String reportedFlowState,
    ChurnState churnState, BigDecimal institutionalBuyValue, BigDecimal institutionalSellValue,
    int institutionalBuyerCount, int institutionalSellerCount, int ruleVersion, Instant computedAt,
    List<ReasonCode> reasons
) {
}
