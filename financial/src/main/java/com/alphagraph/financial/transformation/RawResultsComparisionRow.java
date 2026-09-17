package com.alphagraph.financial.transformation;

/** One quarter as parsed from NSE's real {@code results-comparision} JSON, before numeric/date conversion. */
record RawResultsComparisionRow(
    String symbol, String toDate, String netSale, String netProfit,
    String interestNew, String otherIncomeNew, String profitBeforeTax
) {
}
