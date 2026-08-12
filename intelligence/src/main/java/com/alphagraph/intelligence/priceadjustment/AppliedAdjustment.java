package com.alphagraph.intelligence.priceadjustment;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One BONUS/SPLIT corporate action that affected a given historical price, and the multiplicative factor it contributed - the disclosure record the "announce, don't silently adjust" requirement is built on. */
public record AppliedAdjustment(
    String actionType, LocalDate exDate, Integer ratioNumerator, Integer ratioDenominator, BigDecimal factor
) {
}
