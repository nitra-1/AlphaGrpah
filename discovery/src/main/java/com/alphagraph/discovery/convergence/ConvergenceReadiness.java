package com.alphagraph.discovery.convergence;

/**
 * Stage 4's own readiness taxonomy - distinct from each source domain's own Stage 3
 * {@code READY}/{@code INSUFFICIENT_HISTORY}/{@code MISSING_PREREQUISITE_DATA} (see
 * {@link SourceReadiness}). Computed before any scoring: {@code domainCoverageCount} (domains
 * whose own Stage 3 readiness is usable) &gt;= 3 -&gt; {@code READY}; 1-2 -&gt; {@code
 * PARTIAL_DATA}; 0 -&gt; {@code INSUFFICIENT_DATA}. A missing/unreadable domain must never be
 * silently treated as "that domain is negative" - it stays visibly partial/insufficient.
 */
enum ConvergenceReadiness {
    READY,
    PARTIAL_DATA,
    INSUFFICIENT_DATA
}
