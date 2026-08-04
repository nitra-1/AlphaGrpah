package com.alphagraph.corporate.api;

/** The direction of one management guidance/commentary statement. Structurally identical to {@link Sentiment}/{@link EventSignal} but kept distinct, same reasoning as those two. */
public enum GuidanceDirection {
    POSITIVE,
    NEGATIVE,
    NEUTRAL
}
