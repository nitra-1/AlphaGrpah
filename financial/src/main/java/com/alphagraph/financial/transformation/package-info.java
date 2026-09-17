/**
 * Business + Earnings Inflection evidence, plus Balance-Sheet's INTEREST_EXPENSE piggybacked on
 * the same live collector (Multibagger Discovery Stage 1, Tier 2+3 of the remaining 7 evidence
 * families). Deliberately not wired into the existing sample-only
 * {@code financial.results.FinancialResultsScheduledPipeline} - this reads NSE's real
 * {@code results-comparision} feed directly and feeds a separate evidence table, no shared state
 * table, no DB round-trip for "prior period" (the live feed's own 5 returned quarters are the
 * period history).
 */
package com.alphagraph.financial.transformation;
