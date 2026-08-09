package com.alphagraph.api.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/** mfPercentage/publicPercentage are nullable, matching ownership.shareholding_pattern's own schema. */
public record AddShareholdingRequest(
    @NotBlank String symbol,
    @NotNull LocalDate periodEnd,
    @NotNull BigDecimal promoterPercentage,
    @NotNull BigDecimal fiiPercentage,
    @NotNull BigDecimal diiPercentage,
    BigDecimal mfPercentage,
    BigDecimal publicPercentage
) {
}
