package com.alphagraph.reference.securitymaster;

/** One EQ-series row as parsed from NSE's EQUITY_L.csv, before type conversion. */
record RawSecurityMasterRow(
    String symbol, String companyName, String series, String listingDate, String faceValue, String isin
) {
}
