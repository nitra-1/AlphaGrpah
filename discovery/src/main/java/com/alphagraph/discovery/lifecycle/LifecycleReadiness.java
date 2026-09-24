package com.alphagraph.discovery.lifecycle;

/**
 * Stage 5's own readiness taxonomy - distinct from Stage 4's {@code ConvergenceReadiness}
 * (unreachable from this package anyway - package-private in {@code discovery.convergence}).
 * {@code lifecycleState} is {@code null} for both {@code INSUFFICIENT_HISTORY} and {@code
 * PARTIAL_HISTORY} in v1 (no provisional-state column yet) - only {@code READY} ever produces one
 * of the 7 {@link LifecycleState} values. See {@link DiscoveryLifecycleEngine}'s own javadoc for
 * the full multi-layer gate that produces each value.
 */
enum LifecycleReadiness {
    INSUFFICIENT_HISTORY,
    PARTIAL_HISTORY,
    READY
}
