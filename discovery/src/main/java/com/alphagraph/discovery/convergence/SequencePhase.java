package com.alphagraph.discovery.convergence;

/**
 * Discovery's own copy of the shared 4-phase Stage 3 taxonomy - every domain's own {@code
 * *SequencePhase} enum is package-private and not importable across module boundaries, so, same
 * convention as every domain's own {@code ReasonCode} being a deliberate per-package copy, this
 * is Stage 4's own copy, parsed from each source table's plain {@code sequence_phase} varchar.
 */
enum SequencePhase {
    FORMING,
    PROGRESSING,
    COMPLETE,
    BROKEN
}
