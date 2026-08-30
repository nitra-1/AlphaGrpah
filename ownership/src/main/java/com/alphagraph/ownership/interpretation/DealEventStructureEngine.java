package com.alphagraph.ownership.interpretation;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Behavior-first decision ladder: churn is judged from actual round-trip trading behavior first -
 * {@code churn_state in {HIGH_CHURN, VERY_HIGH_CHURN}} always produces at least
 * {@link EventStructure#HIGH_CHURN_ACTIVITY}. It only escalates to the more specific
 * {@link EventStructure#PROP_CHURN} when PROP_DESK/QUANT_HFT/BROKER participants account for more
 * than half the symbol's matched round-trip value *and* the value-weighted classification
 * confidence of those specific participants is at least 60 - so a low-confidence name-pattern
 * guess can never manufacture the more specific label on its own. Below the churn threshold, the
 * remaining structures are decided from institutional buy/sell value and count shares plus
 * Sprint 2's materiality level.
 */
@Component
class DealEventStructureEngine {

    private static final double PROP_DOMINANCE_THRESHOLD = 0.50;
    private static final double PROP_CONFIDENCE_GATE = 60.0;
    private static final double DIRECTIONAL_LEAN_RATIO = 1.5;

    EventStructure decide(SymbolFlowSummary flow, MaterialityLevel materialityLevel) {
        if (flow.churnState() == ChurnState.HIGH_CHURN || flow.churnState() == ChurnState.VERY_HIGH_CHURN) {
            boolean propDominant = flow.propShareOfMatchedRoundTripValue() > PROP_DOMINANCE_THRESHOLD
                && flow.propWeightedConfidence() >= PROP_CONFIDENCE_GATE;
            return propDominant ? EventStructure.PROP_CHURN : EventStructure.HIGH_CHURN_ACTIVITY;
        }

        boolean netBuying = flow.institutionalBuyValue().compareTo(flow.institutionalSellValue()) > 0;
        boolean netSelling = flow.institutionalSellValue().compareTo(flow.institutionalBuyValue()) > 0;

        if (netBuying && flow.institutionalBuyerCount() >= 3 && materialityLevel.atLeast(MaterialityLevel.HIGH)) {
            return EventStructure.MULTI_INSTITUTION_BUYING;
        }
        if (netBuying && flow.institutionalBuyerCount() == 1 && isSingleDominantRepeatedBuyer(flow)) {
            return EventStructure.SINGLE_INSTITUTION_POSITION_BUILDING;
        }
        if (netBuying && materialityLevel.atLeast(MaterialityLevel.MEDIUM)) {
            return EventStructure.INSTITUTIONAL_BUYING_CANDIDATE;
        }
        if (netSelling && materialityLevel.atLeast(MaterialityLevel.MEDIUM)) {
            return EventStructure.INSTITUTIONAL_SELLING_CANDIDATE;
        }
        if (isClearlyLeaning(flow.totalBuyValue(), flow.totalSellValue())) {
            return EventStructure.DIRECTIONAL_BUYING;
        }
        if (isClearlyLeaning(flow.totalSellValue(), flow.totalBuyValue())) {
            return EventStructure.DIRECTIONAL_SELLING;
        }
        if (flow.churnState() == ChurnState.MIXED) {
            return EventStructure.MIXED_ACTIVITY;
        }
        return EventStructure.UNRESOLVED;
    }

    private static boolean isSingleDominantRepeatedBuyer(SymbolFlowSummary flow) {
        List<ParticipantFlow> institutionalBuyers = flow.participantFlows().stream()
            .filter(f -> f.buyValue().signum() > 0)
            .filter(f -> isInstitutional(f.participantType()))
            .toList();
        if (institutionalBuyers.size() != 1) {
            return false;
        }
        ParticipantFlow theBuyer = institutionalBuyers.get(0);
        boolean dominant = flow.totalBuyValue().signum() > 0
            && theBuyer.buyValue().compareTo(flow.totalBuyValue().multiply(BigDecimal.valueOf(PROP_DOMINANCE_THRESHOLD))) > 0;
        boolean repeated = theBuyer.buySessions() >= 2;
        return dominant && repeated;
    }

    private static boolean isInstitutional(ParticipantType type) {
        return type == ParticipantType.MUTUAL_FUND || type == ParticipantType.INSURANCE
            || type == ParticipantType.FPI_FII || type == ParticipantType.SOVEREIGN_PENSION_FUND
            || type == ParticipantType.AIF;
    }

    private static boolean isClearlyLeaning(BigDecimal larger, BigDecimal smaller) {
        return larger.signum() > 0 && larger.compareTo(smaller.multiply(BigDecimal.valueOf(DIRECTIONAL_LEAN_RATIO))) > 0;
    }
}
