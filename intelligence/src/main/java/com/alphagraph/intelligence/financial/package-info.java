/**
 * Bridges corporate's extracted Financial Results facts into {@code financial.financial_results}
 * - {@code corporate} cannot depend on {@code financial} (docs/001_System_Architecture.md §4 Rule
 * 3), so {@code intelligence} reads {@code corporate.knowledge.FinancialResultFactReader}'s raw
 * fact groups, maps them into {@code financial.api.FinancialResult}, and writes via financial's
 * own {@code FinancialResultsLoader} - the same read-from-one-domain, write-via-another's-own-API
 * shape as {@code intelligence.institutional} and {@code intelligence.technical}.
 */
package com.alphagraph.intelligence.financial;
