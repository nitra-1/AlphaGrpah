package com.alphagraph.market.pricing;

/**
 * One EQ-series row as parsed from NSE's sec_bhavdata_full CSV, before symbol resolution.
 * {@code turnoverLacs} is NSE's own real traded-value figure (TURNOVER_LACS, in INR lakhs) - the
 * genuine exchange-reported turnover for the day, not a {@code close * volume} approximation.
 */
record RawDeliveryRow(
    String symbol, String series, String tradeDate,
    String open, String high, String low, String close,
    String volume, String turnoverLacs, String deliveryPercentage
) {
}
