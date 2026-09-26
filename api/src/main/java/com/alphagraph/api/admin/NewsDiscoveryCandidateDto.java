package com.alphagraph.api.admin;

import java.time.Instant;
import java.util.UUID;

public record NewsDiscoveryCandidateDto(
    String symbol, String companyName, UUID securityMasterId, boolean tracked,
    Instant firstSeenAt, Instant lastSeenAt, int exposureCount, String bestDirection, String bestExposureType, String status
) {
}
