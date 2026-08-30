package com.alphagraph.ownership.interpretation;

/**
 * What a symbol's recent bulk/block trading pattern looks like - judged from actual round-trip
 * trading behavior first, participant-type evidence second. {@link #HIGH_CHURN_ACTIVITY} is the
 * default for high churn; it only escalates to the more specific {@link #PROP_CHURN} when
 * PROP_DESK/QUANT_HFT/BROKER participants confidently account for most of the churned value - see
 * {@link DealEventStructureEngine}.
 */
public enum EventStructure {
    PROP_CHURN,
    HIGH_CHURN_ACTIVITY,
    DIRECTIONAL_BUYING,
    DIRECTIONAL_SELLING,
    INSTITUTIONAL_BUYING_CANDIDATE,
    INSTITUTIONAL_SELLING_CANDIDATE,
    MULTI_INSTITUTION_BUYING,
    SINGLE_INSTITUTION_POSITION_BUILDING,
    MIXED_ACTIVITY,
    UNRESOLVED
}
