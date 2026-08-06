package com.alphagraph.corporate.signal;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One instrument's inputs to the Corporate Signal Engine, the point-in-time roll-up of the four
 * corporate engines already built (Order Book, Management Commentary, News Catalyst, Corporate
 * Event). The three score fields are nullable - an instrument with no history in a given domain
 * simply contributes nothing to that domain's rule, the same null-tolerant pattern
 * {@code intelligence.risk.RiskAnalysisOrchestrator} already established for cross-domain reads.
 * {@code totalEventCount} (considered but not scored directly - see
 * {@link CorporateSignalEngine#confidence}) is distinct from {@code netEventSignal} (POSITIVE
 * minus NEGATIVE signal events in the lookback window, the actual RuleSet input) so confidence can
 * reflect "how much event evidence exists" separately from "which direction it points."
 */
record CorporateSignalInput(
    UUID instrumentId, String symbol, Double orderBookScore, Double managementScore, Double newsCatalystScore,
    int netEventSignal, int totalEventCount, LocalDate asOfDate
) {
}
