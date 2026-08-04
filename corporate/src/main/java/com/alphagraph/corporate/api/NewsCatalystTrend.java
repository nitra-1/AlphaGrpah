package com.alphagraph.corporate.api;

/** The net direction of an instrument's recent news-catalyst history. NONE (not fabricated as MIXED) when no links exist yet. */
public enum NewsCatalystTrend {
    POSITIVE,
    NEGATIVE,
    MIXED,
    NONE
}
