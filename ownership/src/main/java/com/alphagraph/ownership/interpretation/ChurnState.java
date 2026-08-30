package com.alphagraph.ownership.interpretation;

/**
 * Banded {@code matched_round_trip_value / (buy_value + sell_value)} - thresholds from the
 * original spec: {@code < 0.30 DIRECTIONAL}, {@code 0.30-0.60 MIXED}, {@code 0.60-0.80 HIGH_CHURN},
 * {@code >= 0.80 VERY_HIGH_CHURN}.
 */
public enum ChurnState {
    DIRECTIONAL,
    MIXED,
    HIGH_CHURN,
    VERY_HIGH_CHURN
}
