package com.alphagraph.api.portfolio;

/**
 * How a holding's Swing setup has moved since the user's entry (see {@link PositionHealthClassifier}).
 * {@code SWING_SETUP_BROKEN} deliberately does not say "thesis broken" - it only proves the
 * current Swing Rating fell into REDUCE/AVOID, not that the underlying business thesis is
 * invalid. A genuine thesis-invalidation concept (explicit user-set conditions) is a future,
 * separate feature.
 */
enum PositionHealth {
    STRONG,
    STABLE,
    WEAKENING,
    SWING_SETUP_BROKEN
}
