package com.alphagraph.ownership.deals;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@link DealMaterialityEngine}'s output for one deal - persisted verbatim, one row, by
 * {@link DealMaterialityWriter}. {@code materialityScore}/{@code materialityLevel} and
 * {@code reportedNetFlowRatio}/{@code reportedFlowState} are deliberately separate outputs, never
 * blended into each other - see the engine's own doc comment for why.
 */
record DealMaterialityResult(
    UUID discoveredDealId, String symbol, LocalDate dealDate, BigDecimal dealValue,
    BigDecimal adtv20, BigDecimal dealToAdtvRatio, String direction,
    int sameSideClientDealCount20CalendarDays, int distinctSameSideClients20CalendarDays,
    int distinctBuyers20CalendarDays, int distinctSellers20CalendarDays,
    double materialityScore, String materialityLevel,
    BigDecimal reportedBuyValue20CalendarDays, BigDecimal reportedSellValue20CalendarDays,
    BigDecimal reportedNetFlowValue20CalendarDays, double reportedNetFlowRatio, String reportedFlowState,
    int ruleVersion, Instant computedAt
) {
}
