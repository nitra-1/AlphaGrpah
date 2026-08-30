package com.alphagraph.ownership.interpretation;

/**
 * Whether every deal in the interpretation window has been materiality-scored yet - independent
 * of what {@link InstitutionalState}/{@link EventStructure} the interpretation actually lands on.
 * {@code PENDING_DATA} means the resulting state might change once Sprint 2's materiality scoring
 * catches up - most importantly, {@link InstitutionalState#NO_CLEAR_SIGNAL} should never be read
 * as a confident final answer while this is {@code PENDING_DATA}.
 */
enum InterpretationReadiness {
    READY,
    PENDING_DATA
}
