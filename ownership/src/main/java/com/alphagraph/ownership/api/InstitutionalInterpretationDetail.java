package com.alphagraph.ownership.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The latest {@code ownership.institutional_interpretations} row for one symbol, plus its
 * persisted reason codes - for the Discovery page's "Why?" expandable section. Every
 * confirmation-specific field is null when {@code discoveryConfirmationState} is
 * {@code NOT_APPLICABLE} - there's nothing directional to confirm.
 */
public record InstitutionalInterpretationDetail(
    String symbol, LocalDate asOfDate, String eventStructure, String institutionalState,
    String discoveryConfirmationState, boolean confirmationFrozen, LocalDate eventAnchorDate,
    int confirmationSessionsElapsed, BigDecimal confirmationScore, BigDecimal priceConfirmationScore,
    BigDecimal deliveryConfirmationScore, BigDecimal volumeConfirmationScore,
    BigDecimal repeatActivityConfirmationScore, BigDecimal confirmationCoveragePct, double confidence,
    Double materialityScore, String reportedFlowState, String churnState, BigDecimal institutionalBuyValue,
    BigDecimal institutionalSellValue, int institutionalBuyerCount, int institutionalSellerCount,
    String interpretationReadiness, List<InterpretationReason> reasons
) {
}
