package com.alphagraph.api.admin;

import java.time.LocalDate;

public record DiscoveryCandidateDto(
    String symbol, String securityName, int dealCount, int distinctBuyers,
    long totalQuantity, LocalDate firstDealDate, LocalDate latestDealDate,
    Double maxMaterialityScore, String maxMaterialityLevel, Double largestDealToAdtvRatio,
    String eventStructure, String institutionalState, String discoveryConfirmationState,
    Double interpretationConfidence, String churnState
) {
}
