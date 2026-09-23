package com.alphagraph.corporate.transformation;

/**
 * Same 4-value taxonomy for every Stage 3 domain (docs/008 §3). {@code COMPLETE} means the
 * required repeat count was reached - never a claim that the capital return/raise was good or bad
 * for shareholders. Expiry is represented as {@code BROKEN} with reason {@code SEQUENCE_EXPIRED},
 * not a 5th phase - and, unlike a state-transition chain, a {@code COMPLETE} repeat cluster here
 * can still expire to {@code BROKEN} if the underlying event stream goes silent for longer than
 * its own repeat window (see {@code CapitalAllocationTransformationSequenceEngine}'s javadoc).
 */
enum CapitalAllocationSequencePhase {
    FORMING,
    PROGRESSING,
    COMPLETE,
    BROKEN
}
