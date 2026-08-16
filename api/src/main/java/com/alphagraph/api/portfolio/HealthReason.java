package com.alphagraph.api.portfolio;

/**
 * Why a {@link PositionHealth} decline happened, not just that it happened - which of the six
 * domain scores drove it, or whether the score barely moved but the instrument's relative
 * standing fell anyway (see {@link PositionHealthClassifier}). {@code null} whenever there's
 * nothing meaningful to attribute (a small/no decline with no material domain move and no
 * material rank fall) - never forced onto noise.
 */
enum HealthReason {
    MARKET_SETUP_WEAKENING,
    BUSINESS_QUALITY_WEAKENING,
    RISK_DETERIORATION,
    BROAD_BASED_WEAKENING,
    RELATIVE_RANK_WEAKENING
}
