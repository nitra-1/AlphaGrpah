package com.alphagraph.api.admin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DiscoveredDealDetailDto(
    UUID id, LocalDate dealDate, String clientName, String buySell,
    long quantity, BigDecimal price, BigDecimal dealValue, String dealType, boolean isDuplicate,
    Double materialityScore, String materialityLevel, Double dealToAdtvRatio, String reportedFlowState
) {
}
