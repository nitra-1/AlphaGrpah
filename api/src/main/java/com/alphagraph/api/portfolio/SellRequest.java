package com.alphagraph.api.portfolio;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record SellRequest(@NotNull UUID instrumentId, @NotNull @Positive BigDecimal quantity) {
}
