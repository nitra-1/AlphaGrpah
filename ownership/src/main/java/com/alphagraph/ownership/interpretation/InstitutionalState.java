package com.alphagraph.ownership.interpretation;

/**
 * What the event structure might mean - deliberately has no "CONFIRMED_*" variants, since
 * confirmation is a fully separate field ({@link DiscoveryConfirmationState}), never blended in.
 * Both {@link EventStructure#PROP_CHURN} and {@link EventStructure#HIGH_CHURN_ACTIVITY} map to
 * {@link #HIGH_CHURN} here - the extra granularity lives in event structure, not here.
 */
public enum InstitutionalState {
    NO_CLEAR_SIGNAL,
    HIGH_CHURN,
    POSSIBLE_ACCUMULATION,
    POSSIBLE_DISTRIBUTION,
    MIXED_ACTIVITY
}
