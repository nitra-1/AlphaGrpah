package com.alphagraph.corporate.api;

/** How one news item affects one company. Structurally identical to {@link Sentiment}/{@link GuidanceDirection} but kept distinct, same reasoning as those two. */
public enum NewsImpactDirection {
    POSITIVE,
    NEGATIVE,
    NEUTRAL
}
