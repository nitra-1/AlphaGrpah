package com.alphagraph.market.transformation;

/**
 * Matches {@code market.inflection_states.primary_state}'s CHECK constraint exactly. Not mutually
 * exclusive in what actually "fires" - {@link MarketInflectionEngine} picks one of these as the
 * {@code primaryState} via a hardcoded priority ladder, but every state that fired is still
 * recorded as a reason code, same convention as {@code ownership.transformation.TransformationState}.
 */
enum MarketInflectionState {
    NO_CLEAR_SIGNAL,
    RELATIVE_VOLUME_EXPANSION,
    DELIVERY_EXPANSION,
    SUSTAINED_DELIVERY_ACCUMULATION,
    STEALTH_ACCUMULATION_CANDIDATE,
    EARLY_PRICE_PARTICIPATION
}
