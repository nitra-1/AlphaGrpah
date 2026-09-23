package com.alphagraph.corporate.transformation;

/**
 * Matches {@code corporate.transformation_sequences.sequence_type}'s CHECK constraint exactly. An
 * instrument may legitimately have both simultaneously - a buyback and a rights issue are
 * evaluated as two fully independent clusters, never blended (docs/008 §14.4).
 *
 * <p>Reserved, not implemented (docs/008 §14.4): {@code INSTITUTIONAL_CAPITAL_RAISE_CYCLE},
 * {@code STRATEGIC_CAPITAL_INJECTION}, {@code DILUTION_PRESSURE}, {@code CAPITAL_RETURN_CLUSTER} -
 * deliberately deferred until real event-type classification (QIP/preferential/warrant/pricing/
 * size/share-count) exists upstream, not forgotten.
 */
enum CapitalAllocationSequenceType {
    REPEATED_CAPITAL_RETURN,
    REPEATED_EQUITY_RAISE
}
