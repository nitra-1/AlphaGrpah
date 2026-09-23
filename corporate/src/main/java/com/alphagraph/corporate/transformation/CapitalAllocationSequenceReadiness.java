package com.alphagraph.corporate.transformation;

/**
 * Persisted separately from {@code corporate.transformation_sequences}
 * ({@code corporate.transformation_sequence_readiness}), never entangled with sequence-row
 * absence - "no sequence row" must stay distinguishable between "genuinely nothing formed"
 * ({@code READY}) and "not enough real history to tell" ({@code INSUFFICIENT_HISTORY}).
 * {@code MISSING_PREREQUISITE_DATA} is part of the shared Stage 3 taxonomy but structurally
 * unreachable in practice - the orchestrator only ever evaluates instruments
 * {@code CapitalAllocationEvidenceReader.findAllInstrumentIds()} already returned real evidence
 * for.
 */
enum CapitalAllocationSequenceReadiness {
    READY,
    INSUFFICIENT_HISTORY,
    MISSING_PREREQUISITE_DATA
}
