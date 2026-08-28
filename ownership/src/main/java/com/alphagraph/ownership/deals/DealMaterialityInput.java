package com.alphagraph.ownership.deals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One {@code discovered_deals} row's resolved inputs to {@link DealMaterialityEngine} - real
 * 20-trading-session ADTV from {@link MarketLiquidityReader} plus the direction-aware 20-calendar-
 * day context from {@link BulkDealContextReader}. {@code sameSideClientDealCount20CalendarDays}/
 * {@code distinctSameSideClients20CalendarDays} are already resolved to this deal's own
 * {@code direction} by the reader that produced them - the engine itself never needs to re-branch
 * on direction to score them, only to classify the deal's own side for storage.
 */
record DealMaterialityInput(
    UUID discoveredDealId, String symbol, LocalDate dealDate, BigDecimal dealValue, String direction,
    BigDecimal adtv20,
    int sameSideClientDealCount20CalendarDays, int distinctSameSideClients20CalendarDays,
    int distinctBuyers20CalendarDays, int distinctSellers20CalendarDays,
    BigDecimal reportedBuyValue20CalendarDays, BigDecimal reportedSellValue20CalendarDays
) {
}
