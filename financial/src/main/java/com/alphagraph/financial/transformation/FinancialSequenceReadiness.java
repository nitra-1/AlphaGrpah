package com.alphagraph.financial.transformation;

/**
 * Persisted separately from {@code financial.transformation_sequences} (see
 * {@code financial.transformation_sequence_readiness}), never entangled with sequence-row absence -
 * "no sequence row" must stay distinguishable between "genuinely nothing formed" ({@code READY})
 * and "not enough real history to tell" ({@code INSUFFICIENT_HISTORY}). {@code MISSING_PREREQUISITE_DATA}
 * is part of the shared Stage 3 taxonomy but structurally unreachable for Financial specifically -
 * all 4 sequences depend on the single {@code financial.inflection_states} table.
 */
enum FinancialSequenceReadiness {
    READY,
    INSUFFICIENT_HISTORY,
    MISSING_PREREQUISITE_DATA
}
