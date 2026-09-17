package com.alphagraph.intelligence.sectorcontext;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One instrument's {@code PRICE_RETURN_20D} evidence value on one trading day, read back from {@code market.transformation_evidence}. */
record PriceReturnPoint(LocalDate tradeDate, BigDecimal value) {
}
