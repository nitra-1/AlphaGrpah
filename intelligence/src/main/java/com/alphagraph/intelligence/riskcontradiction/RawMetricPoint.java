package com.alphagraph.intelligence.riskcontradiction;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One raw Stage 1 evidence row's {@code value}/{@code change}/{@code confidence} - used for the 2
 * triggers that need a raw metric reading rather than another family's already-decided Stage 2
 * state ({@code OPERATING_MARGIN}'s change, {@code PRICE_RETURN_20D}'s value/change). Carries
 * {@code value} (not just {@code change}) so a trigger can require genuine positive/negative level,
 * not merely a large swing that never actually crosses zero (see
 * {@code PRICE_WITHOUT_DELIVERY_CONFIRMATION}'s trigger). Carries the source row's own real
 * {@code confidence} too, never a fixed bucket - the same real number every other Stage 2 engine
 * already computes for it.
 */
record RawMetricPoint(LocalDate asOfDate, BigDecimal value, BigDecimal change, double confidence) {
}
