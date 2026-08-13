package com.alphagraph.market.pricing;

/** One index's row as parsed from NSE's ind_close_all CSV, before symbol resolution - see {@link IndexBhavdataParser}. */
record RawIndexRow(String indexName, String tradeDate, String open, String high, String low, String close, String volume) {
}
