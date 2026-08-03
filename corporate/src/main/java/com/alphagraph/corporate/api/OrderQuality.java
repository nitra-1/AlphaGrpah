package com.alphagraph.corporate.api;

/**
 * The Order Book Engine's qualitative rating, derived from the quantitative order-book quality
 * score via the same static-threshold-banding convention every Phase 1 engine uses for its own
 * Level enum (e.g. risk.api.RiskLevel).
 */
public enum OrderQuality {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR
}
