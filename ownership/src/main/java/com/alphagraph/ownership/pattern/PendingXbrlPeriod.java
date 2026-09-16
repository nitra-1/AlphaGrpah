package com.alphagraph.ownership.pattern;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One shareholding quarter still missing its XBRL-derived institutional sub-category breakdown. */
record PendingXbrlPeriod(UUID instrumentId, String symbol, LocalDate periodEnd, String xbrlUrl, BigDecimal storedPromoterPercentage) {
}
