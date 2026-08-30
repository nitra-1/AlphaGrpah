package com.alphagraph.ownership.interpretation;

import java.math.BigDecimal;
import java.util.List;

/**
 * Symbol-level roll-up from {@link ParticipantFlowAnalyzer} - {@code matchedRoundTripValue} is the
 * *sum of each participant's own* {@code 2 x MIN(buyValue, sellValue)}, not a naive symbol-wide
 * {@code 2 x MIN(totalBuyValue, totalSellValue)}. That distinction is load-bearing: a symbol where
 * institutional buyers absorb a large seller's stock (different participants on each side) would
 * otherwise show total buy roughly equal to total sell and get misread as "churny," when nothing
 * actually round-tripped - it was a genuine ownership transition. Only value the *same* entity
 * genuinely bought and sold back counts toward churn.
 */
record SymbolFlowSummary(
    BigDecimal totalBuyValue, BigDecimal totalSellValue, BigDecimal matchedRoundTripValue,
    double churnRatio, ChurnState churnState,
    BigDecimal institutionalBuyValue, BigDecimal institutionalSellValue,
    int institutionalBuyerCount, int institutionalSellerCount,
    BigDecimal propMatchedRoundTripValue, double propShareOfMatchedRoundTripValue,
    double propWeightedConfidence,
    List<ParticipantFlow> participantFlows
) {
}
