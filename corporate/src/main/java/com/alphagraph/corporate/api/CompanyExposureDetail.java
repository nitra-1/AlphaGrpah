package com.alphagraph.corporate.api;

import java.util.UUID;

/**
 * One {@code corporate.economic_event_company_exposures} row, with {@code matchedSymbol} resolved
 * at read time from whichever of {@code reference.instruments}/{@code reference.security_master}
 * {@code matchType} points at ({@code UNRESOLVED} rows carry only {@code companyNameRaw}, never a
 * symbol - see {@code corporate.news.CompanyResolver}). {@code tracked} is true only for
 * {@code EXACT_INSTRUMENT} matches; every other resolved match is a real, untracked NSE company.
 */
public record CompanyExposureDetail(
    UUID matchedInstrumentId, UUID matchedSecurityMasterId, String matchedSymbol, boolean tracked,
    String companyNameRaw, String matchType, String exposureType, String direction,
    String impactStrength, double confidence, String reason
) {
}
