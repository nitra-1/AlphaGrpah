package com.alphagraph.market.transformation;

/**
 * Matches {@code market.transformation_sequences.sequence_type}'s CHECK constraint exactly.
 * A stock may legitimately have multiple of these active simultaneously - Stage 3 never picks one
 * "primary" sequence (docs/008 §8/§49), so no priority ladder exists here.
 */
enum MarketSequenceType {
    DELIVERY_LED_ACCUMULATION,
    STEALTH_ACCUMULATION_SEQUENCE,
    MARKET_RECOGNITION_SEQUENCE
}
