package com.alphagraph.discovery.convergence;

/**
 * The convergence classification computed strictly from the raw, pre-penalty picture alone -
 * never includes {@link ConvergenceState#CONVERGENCE_WITH_CONTRADICTIONS}, which is a
 * display-only overlay applied afterward (round-2 correction 2: this must never be computed from
 * a mix of raw/final score, and never influenced by the contradiction penalty). A separate type
 * from {@link ConvergenceState} so it's structurally impossible to assign the overlay value here.
 */
enum PreContradictionState {
    NO_CONVERGENCE,
    EARLY_CONVERGENCE,
    MULTI_DOMAIN_INFLECTION,
    STRONG_CONVERGENCE
}
