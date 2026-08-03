package com.alphagraph.corporate.api;

/**
 * A document's overall tone, as judged by the Document Intelligence Engine's canonical
 * extraction. Structurally identical to {@link EventSignal} but conceptually distinct - this is
 * the document's tone, not a specific detected event's investment-thesis direction - kept as a
 * separate type rather than reused, since the two can diverge (a neutrally-worded document can
 * still describe a positive-signal event).
 */
public enum Sentiment {
    POSITIVE,
    NEGATIVE,
    NEUTRAL
}
