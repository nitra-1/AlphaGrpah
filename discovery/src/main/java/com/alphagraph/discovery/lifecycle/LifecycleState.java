package com.alphagraph.discovery.lifecycle;

/**
 * The 7-state transformation lifecycle taxonomy. Deliberately never BUY/SELL/TARGET_PRICE/
 * EXPECTED_RETURN/MULTIBAGGER_PROBABILITY/SUCCESS_PROBABILITY - Stage 5 classifies trajectory
 * through the transformation, never investment outcome. {@code DETERIORATING} is an orthogonal
 * decline state, never itself "higher" or "lower" than any of the other 6 - see {@link
 * DiscoveryLifecycleEngine}'s own javadoc for the separate cycle-peak ranking that excludes both
 * {@code DORMANT} and {@code DETERIORATING}.
 */
enum LifecycleState {
    DORMANT,
    EARLY_INFLECTION,
    EMERGING,
    ACCELERATING,
    MARKET_RECOGNITION,
    MATURE_RERATING,
    DETERIORATING
}
