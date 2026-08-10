package com.alphagraph.corporate.knowledge;

import java.util.List;

/**
 * Mirrors the JSON shape {@link FinancialResultsExtractor} constrains Claude's response to. A
 * single Financial Results filing always presents several period columns at once (the current
 * quarter, the immediately preceding quarter, the year-ago quarter, the full year), so this is a
 * list - one document can retroactively fill several {@code financial.financial_results} rows.
 */
record LlmFinancialResultsExtractionResponse(List<LlmFinancialResultPeriod> periods) {
}

/**
 * One column of a Financial Results table. Monetary fields are reported in {@code sourceUnit} as
 * stated in the table header (e.g. "Rs. in Crore" vs "Rs. in million") - {@link
 * FinancialResultsExtractor} converts to Crore deterministically in Java rather than asking the
 * model to do the arithmetic itself, the same "compute deterministically, don't trust the model
 * with arithmetic" discipline {@code OrderExtractor.computeExecutionEndYear} already established.
 * {@code eps}/{@code roePercentage}/{@code rocePercentage}/{@code operatingMarginPercentage}/
 * {@code netMarginPercentage} are per-share or percentage figures and are never unit-converted.
 */
record LlmFinancialResultPeriod(
    String periodEnd, String periodType, String sourceUnit,
    String sales, String pat, String eps,
    String roePercentage, String rocePercentage, String operatingMarginPercentage, String netMarginPercentage,
    String cashFlowFromOperations, String totalAssets, String currentAssets, String currentLiabilities,
    String totalDebt, String totalEquity, String interestExpense, String ebit,
    int confidence
) {
}
