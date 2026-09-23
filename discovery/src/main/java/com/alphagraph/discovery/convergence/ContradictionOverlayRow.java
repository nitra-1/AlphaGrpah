package com.alphagraph.discovery.convergence;

import java.time.LocalDate;
import java.util.List;

/** One real {@code risk.contradiction_states} row, the most recent as-of a given date, plus its own {@code risk.contradiction_state_reasons} codes. The reader stays freshness-agnostic - {@link DiscoveryConvergenceEngine} applies the {@code stage4-risk-contradiction-max-age-days} gate itself against this row's own {@code asOfDate}. */
record ContradictionOverlayRow(String primaryState, double confidence, LocalDate asOfDate, List<String> reasonCodes) {
}
