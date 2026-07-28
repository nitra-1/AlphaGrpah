package com.alphagraph.financial.results;

/** One row as parsed from the sample financial results CSV, before symbol resolution. */
record RawFinancialResultRow(
    String symbol, String periodEnd, String periodType, String sales, String pat, String eps,
    String roePct, String rocePct, String operatingMarginPct, String netMarginPct, String cashFlowFromOps
) {
}
