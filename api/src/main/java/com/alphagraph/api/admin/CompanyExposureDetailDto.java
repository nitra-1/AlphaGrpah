package com.alphagraph.api.admin;

import java.util.UUID;

public record CompanyExposureDetailDto(
    UUID matchedInstrumentId, UUID matchedSecurityMasterId, String matchedSymbol, boolean tracked,
    String companyNameRaw, String matchType, String exposureType, String direction,
    String impactStrength, double confidence, String reason
) {
}
