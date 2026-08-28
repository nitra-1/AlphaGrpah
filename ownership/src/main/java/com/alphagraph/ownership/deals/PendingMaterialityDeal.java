package com.alphagraph.ownership.deals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One {@code discovered_deals} row not yet scored - {@link PendingMaterialityDealReader}'s output. */
record PendingMaterialityDeal(
    UUID id, String symbol, LocalDate dealDate, BigDecimal dealValue, String buySell, String clientNameNormalized
) {
}
