package com.alphagraph.risk.api;

/**
 * A risk band, used for every one of the Risk Engine's five outputs (Business, Technical,
 * Ownership, Valuation, Overall). VERY_LOW is safest, VERY_HIGH is most dangerous.
 */
public enum RiskLevel {
    VERY_LOW,
    LOW,
    MEDIUM,
    HIGH,
    VERY_HIGH
}
