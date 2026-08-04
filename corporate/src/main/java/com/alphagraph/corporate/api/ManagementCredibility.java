package com.alphagraph.corporate.api;

/**
 * A proxy for how much weight to put on management's commentary - derived from the consistency of
 * direction and commitment level across observations over time, NOT from comparing past guidance
 * to actual outcomes (no infrastructure exists yet to cross-reference guidance against
 * Fundamental Engine's real results) - a real, disclosed simplification.
 */
public enum ManagementCredibility {
    LOW,
    MEDIUM,
    HIGH
}
