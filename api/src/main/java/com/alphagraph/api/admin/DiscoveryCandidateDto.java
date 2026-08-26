package com.alphagraph.api.admin;

import java.time.LocalDate;

public record DiscoveryCandidateDto(
    String symbol, String securityName, int dealCount, int distinctBuyers,
    long totalQuantity, LocalDate firstDealDate, LocalDate latestDealDate
) {
}
