package com.alphagraph.intelligence.riskcontradiction;

/**
 * Matches {@code risk.contradiction_states.primary_state}'s CHECK constraint exactly. No priority
 * ladder within this family (§13) - multiple contradictions co-existing is itself the signal
 * ({@code MULTI_DOMAIN_CONTRADICTION}), so every contributing contradiction's reasons stay
 * recorded regardless of which single state occupies this slot.
 */
enum RiskContradictionState {
    NO_CLEAR_SIGNAL,
    GROWTH_QUALITY_CONTRADICTION,
    OWNERSHIP_CONTRADICTION,
    PRICE_WITHOUT_DELIVERY_CONFIRMATION,
    CAPITAL_RAISE_WITH_WEAK_BUSINESS_INFLECTION,
    MULTI_DOMAIN_CONTRADICTION
}
