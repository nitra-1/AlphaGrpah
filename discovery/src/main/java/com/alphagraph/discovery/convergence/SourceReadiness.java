package com.alphagraph.discovery.convergence;

/** Discovery's own copy of every Stage 3 domain's shared readiness taxonomy, parsed from each source table's {@code readiness} varchar. */
enum SourceReadiness {
    READY,
    INSUFFICIENT_HISTORY,
    MISSING_PREREQUISITE_DATA
}
