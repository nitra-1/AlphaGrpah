package com.alphagraph.ownership.deals;

/**
 * One row as parsed from the combined bulk/block deals feed, before symbol resolution.
 * {@code dealType} ("BULK" or "BLOCK") is stamped on by the Collector, not present in either
 * real NSE file itself - it tags which of the two real reports a row came from.
 */
record RawDealRow(
    String dealType, String symbol, String dealDate, String clientName,
    String buySell, String quantity, String price
) {
}
