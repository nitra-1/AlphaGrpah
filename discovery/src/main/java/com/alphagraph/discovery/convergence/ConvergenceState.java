package com.alphagraph.discovery.convergence;

/**
 * The display-level Stage 4 classification. Deliberately never MULTIBAGGER/HIGH_CONVICTION/BUY/
 * STRONG_BUY/WINNER - Stage 4 detects convergence, not investment outcome. {@code
 * CONVERGENCE_WITH_CONTRADICTIONS} only ever overlays an actual positive
 * {@code pre_contradiction_state} ({@link #EARLY_CONVERGENCE}/{@link #MULTI_DOMAIN_INFLECTION}/
 * {@link #STRONG_CONVERGENCE}) - {@link #NO_CONVERGENCE} plus a live contradiction stays
 * {@code NO_CONVERGENCE}, since there is no convergence to contradict (see
 * {@code DiscoveryConvergenceEngine}'s javadoc, round-2 correction 1).
 */
enum ConvergenceState {
    NO_CONVERGENCE,
    EARLY_CONVERGENCE,
    MULTI_DOMAIN_INFLECTION,
    STRONG_CONVERGENCE,
    CONVERGENCE_WITH_CONTRADICTIONS
}
