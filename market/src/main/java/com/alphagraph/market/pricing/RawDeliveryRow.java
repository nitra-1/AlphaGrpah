package com.alphagraph.market.pricing;

/** One EQ-series row as parsed from NSE's sec_bhavdata_full CSV, before symbol resolution. */
record RawDeliveryRow(
    String symbol, String series, String tradeDate,
    String open, String high, String low, String close,
    String volume, String deliveryPercentage
) {
}
