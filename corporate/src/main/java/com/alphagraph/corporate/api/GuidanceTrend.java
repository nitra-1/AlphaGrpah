package com.alphagraph.corporate.api;

/** How an instrument's revenue guidance has moved across recent observations. UNKNOWN (not fabricated as STABLE) when fewer than 2 numeric observations exist to compare. */
public enum GuidanceTrend {
    UPGRADING,
    STABLE,
    DOWNGRADING,
    UNKNOWN
}
