package com.alphagraph.ownership.transformation;

/**
 * Matches {@code ownership.transformation_states.transformation_state}'s CHECK constraint exactly.
 * Not mutually exclusive in what actually "fires" - {@link OwnershipTransformationEngine} picks one
 * of these as the {@code primary_state} via a hardcoded priority ladder, but every state that fired
 * is still recorded as a reason code, so a higher-priority state never hides a lower one that also
 * applied.
 */
enum TransformationState {
    NO_CLEAR_SIGNAL,
    PROMOTER_HOLDING_INCREASE,
    PROMOTER_DILUTION,
    FII_ACCUMULATION,
    DII_ACCUMULATION,
    INSTITUTIONAL_OWNERSHIP_EXPANSION,
    BULK_BUYING_WITH_OWNERSHIP_EXPANSION,
    OWNERSHIP_CONTRADICTION
}
