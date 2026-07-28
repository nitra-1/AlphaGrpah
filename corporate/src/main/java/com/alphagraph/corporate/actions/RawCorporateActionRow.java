package com.alphagraph.corporate.actions;

/** One row as parsed from the sample corporate actions CSV, before symbol resolution. */
record RawCorporateActionRow(
    String symbol, String actionType, String announcementDate, String exDate, String recordDate,
    String dividendAmount, String ratioNumerator, String ratioDenominator, String price
) {
}
