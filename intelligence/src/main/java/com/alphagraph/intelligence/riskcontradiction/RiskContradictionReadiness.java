package com.alphagraph.intelligence.riskcontradiction;

/**
 * Distinct from {@code RiskContradictionState} - reports how many of the (up to 6) real
 * cross-family reads this computation actually found data for, independent of whether a
 * contradiction fired. Exists so {@code NO_CLEAR_SIGNAL} stays honestly distinguishable between
 * "every source is real and calm" ({@code READY}) and "most sources don't have real evidence for
 * this instrument yet" ({@code PARTIAL_DATA}/{@code INSUFFICIENT_DATA}) - same reasoning
 * {@code sector.transformation.SectorDataReadiness} already established for Sector's own Stage 2.
 */
enum RiskContradictionReadiness {
    READY,
    PARTIAL_DATA,
    INSUFFICIENT_DATA
}
