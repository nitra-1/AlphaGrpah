package com.alphagraph.api.portfolio;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/** rationale is optional context for the Trade Journal entry this buy auto-records (Module 3.8). */
public record BuyRequest(
    @NotNull UUID instrumentId, @NotNull @Positive BigDecimal quantity, @NotNull @Positive BigDecimal price, String rationale
) {
}
