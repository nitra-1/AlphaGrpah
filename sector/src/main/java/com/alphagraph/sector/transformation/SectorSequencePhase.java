package com.alphagraph.sector.transformation;

/**
 * Same 4-value taxonomy for every Stage 3 domain (docs/008 §3). {@code COMPLETE} means the
 * evidence sequence completed - never a claim that the investment thesis succeeded. Expiry is
 * represented as {@code BROKEN} with reason {@code SEQUENCE_EXPIRED}, not a 5th phase.
 */
enum SectorSequencePhase {
    FORMING,
    PROGRESSING,
    COMPLETE,
    BROKEN
}
