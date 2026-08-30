package com.alphagraph.ownership.interpretation;

/**
 * Whether subsequent market behavior (price/delivery/volume/repeat activity, all reachable for an
 * untracked symbol via {@code market.discovered_prices}) supports a directional
 * {@link InstitutionalState}, decided within a bounded T+1/T+3/T+5-trading-session window and
 * frozen at T+5 - never judged forever against a moving target. {@link #NOT_APPLICABLE} for every
 * non-directional institutional state ({@code HIGH_CHURN}/{@code MIXED_ACTIVITY}/
 * {@code NO_CLEAR_SIGNAL}) - there's nothing directional to confirm.
 */
public enum DiscoveryConfirmationState {
    PENDING,
    PARTIALLY_CONFIRMED,
    CONFIRMED,
    FAILED,
    NOT_APPLICABLE
}
