package com.alphagraph.ownership.transformation;

/**
 * Persisted separately from {@code ownership.transformation_sequences} (see
 * {@code ownership.transformation_sequence_readiness}), never entangled with sequence-row absence -
 * "no sequence row" must stay distinguishable between "genuinely nothing formed" ({@code READY})
 * and "not enough real history to tell" ({@code INSUFFICIENT_HISTORY}), the same lesson
 * {@code market.transformation.MarketSequenceReadiness} already established. {@code MISSING_PREREQUISITE_DATA}
 * is part of the shared Stage 3 taxonomy but structurally unreachable for Ownership specifically -
 * all 3 sequences depend on the single {@code ownership.transformation_states} table.
 */
enum OwnershipSequenceReadiness {
    READY,
    INSUFFICIENT_HISTORY,
    MISSING_PREREQUISITE_DATA
}
