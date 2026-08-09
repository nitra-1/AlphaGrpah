package com.alphagraph.reference.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One EQ-series entry from NSE's full listed-securities master - not necessarily tracked. */
public record SecurityMasterEntry(
    UUID id, String symbol, String companyName, String isin, LocalDate listingDate, BigDecimal faceValue
) {
}
