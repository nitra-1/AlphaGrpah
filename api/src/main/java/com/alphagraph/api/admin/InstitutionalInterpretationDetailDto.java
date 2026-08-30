package com.alphagraph.api.admin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InstitutionalInterpretationDetailDto(
    String symbol, LocalDate asOfDate, String eventStructure, String institutionalState,
    String discoveryConfirmationState, boolean confirmationFrozen, LocalDate eventAnchorDate,
    int confirmationSessionsElapsed, BigDecimal confirmationScore, BigDecimal priceConfirmationScore,
    BigDecimal deliveryConfirmationScore, BigDecimal volumeConfirmationScore,
    BigDecimal repeatActivityConfirmationScore, BigDecimal confirmationCoveragePct, double confidence,
    Double materialityScore, String reportedFlowState, String churnState, BigDecimal institutionalBuyValue,
    BigDecimal institutionalSellValue, int institutionalBuyerCount, int institutionalSellerCount,
    List<InterpretationReasonDto> reasons
) {
}
