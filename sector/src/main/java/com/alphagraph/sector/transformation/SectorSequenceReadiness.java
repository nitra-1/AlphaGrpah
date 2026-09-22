package com.alphagraph.sector.transformation;

/**
 * Persisted separately from {@code sector.transformation_sequences} (see
 * {@code sector.transformation_sequence_readiness}), never entangled with sequence-row absence -
 * "no sequence row" must stay distinguishable between "genuinely nothing formed" ({@code READY})
 * and "not enough real history to tell" ({@code INSUFFICIENT_HISTORY}). {@code MISSING_PREREQUISITE_DATA}
 * is part of the shared Stage 3 taxonomy but structurally unreachable for Sector specifically - all
 * 3 sequences depend on the single {@code sector.inflection_states} table.
 */
enum SectorSequenceReadiness {
    READY,
    INSUFFICIENT_HISTORY,
    MISSING_PREREQUISITE_DATA
}
