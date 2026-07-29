package com.alphagraph.ownership.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One real bulk or block deal trade for one instrument, mirroring
 * {@code ownership.bulk_deals} (docs/003_Database_Architecture.md §3a). {@code dealType}
 * distinguishes a bulk deal (single trade >= 0.5% of the company's shares) from a block deal
 * (large trade in NSE's special window, >= 5 lakh shares or >= INR 5 crore).
 */
public record BulkDeal(
    UUID instrumentId, String symbol, LocalDate dealDate, String clientName,
    String buySell, long quantity, BigDecimal price, String dealType
) {
}
