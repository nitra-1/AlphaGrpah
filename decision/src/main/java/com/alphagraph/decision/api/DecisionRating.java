package com.alphagraph.decision.api;

/**
 * Five-band rating for a {@link DecisionScore}'s swing or long-term score - investment-flavored
 * naming (unlike {@code corporate.api.CorporateRating}'s EXCELLENT/STRONG/NEUTRAL/WEAK/POOR)
 * since Swing/Long-Term Score are the roadmap's own "should I act on this" composites, not a
 * narrower domain signal. Same band edges as every prior 5-band rating in this project (80/65/40/25).
 */
public enum DecisionRating {
    STRONG_BUY,
    BUY,
    HOLD,
    REDUCE,
    AVOID
}
