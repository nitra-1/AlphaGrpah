package com.alphagraph.market.transformation;

/**
 * Persisted separately from {@code market.transformation_sequences} (see
 * {@code market.transformation_sequence_readiness}), never entangled with sequence-row absence -
 * "no sequence row" must stay distinguishable between "genuinely nothing formed" ({@code READY})
 * and "not enough real history to tell" ({@code INSUFFICIENT_HISTORY}), the same lesson
 * {@code sector.transformation.SectorDataReadiness} already established. {@code MISSING_PREREQUISITE_DATA}
 * is part of the shared Stage 3 taxonomy but structurally unreachable for Market specifically - all
 * 3 Market sequences depend on the single {@code market.inflection_states} table.
 */
enum MarketSequenceReadiness {
    READY,
    INSUFFICIENT_HISTORY,
    MISSING_PREREQUISITE_DATA
}
