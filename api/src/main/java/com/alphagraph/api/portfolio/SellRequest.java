package com.alphagraph.api.portfolio;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * price is required as of Module 3.8 - a real gap surfaced building the Trade Journal: Module
 * 3.3's sell never needed an execution price (unrealized P&L only depends on the remaining
 * position's average cost basis), but recording realized P&L for the journal is impossible
 * without knowing what the shares actually sold at. rationale is optional Trade Journal context.
 */
public record SellRequest(
    @NotNull UUID instrumentId, @NotNull @Positive BigDecimal quantity, @NotNull @Positive BigDecimal price, String rationale
) {
}
