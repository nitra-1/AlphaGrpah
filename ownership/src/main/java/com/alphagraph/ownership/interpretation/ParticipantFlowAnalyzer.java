package com.alphagraph.ownership.interpretation;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Pure computation, no I/O - takes an explicit list of {@link ParticipantDealActivity} (the
 * caller decides the window: the 20-calendar-day interpretation window for event structure and
 * repeat-behavior reason codes, or {@link DiscoveryConfirmationEngine}'s strictly-post-anchor
 * window for real follow-through evidence - this class never assumes which).
 */
@Component
class ParticipantFlowAnalyzer {

    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    SymbolFlowSummary analyze(List<ParticipantDealActivity> activities) {
        Map<UUID, List<ParticipantDealActivity>> byParticipant = activities.stream()
            .collect(Collectors.groupingBy(ParticipantDealActivity::participantId));

        List<ParticipantFlow> flows = new ArrayList<>();
        for (List<ParticipantDealActivity> participantActivities : byParticipant.values()) {
            flows.add(flowFor(participantActivities));
        }

        BigDecimal totalBuyValue = sum(flows, ParticipantFlow::buyValue);
        BigDecimal totalSellValue = sum(flows, ParticipantFlow::sellValue);
        BigDecimal matchedRoundTripValue = sum(flows, ParticipantFlow::matchedRoundTripValue);
        double churnRatio = churnRatio(matchedRoundTripValue, totalBuyValue.add(totalSellValue));
        ChurnState churnState = bandChurn(churnRatio);

        BigDecimal institutionalBuyValue = sum(institutional(flows), ParticipantFlow::buyValue);
        BigDecimal institutionalSellValue = sum(institutional(flows), ParticipantFlow::sellValue);
        int institutionalBuyerCount = (int) institutional(flows).stream().filter(f -> f.buyValue().signum() > 0).count();
        int institutionalSellerCount = (int) institutional(flows).stream().filter(f -> f.sellValue().signum() > 0).count();

        List<ParticipantFlow> propFlows = propType(flows);
        BigDecimal propMatchedRoundTripValue = sum(propFlows, ParticipantFlow::matchedRoundTripValue);
        double propShare = matchedRoundTripValue.signum() == 0
            ? 0.0
            : propMatchedRoundTripValue.divide(matchedRoundTripValue, 6, RoundingMode.HALF_UP).doubleValue();
        double propWeightedConfidence = weightedConfidence(propFlows);

        return new SymbolFlowSummary(
            totalBuyValue, totalSellValue, matchedRoundTripValue, churnRatio, churnState,
            institutionalBuyValue, institutionalSellValue, institutionalBuyerCount, institutionalSellerCount,
            propMatchedRoundTripValue, propShare, propWeightedConfidence, flows
        );
    }

    private static ParticipantFlow flowFor(List<ParticipantDealActivity> activities) {
        ParticipantDealActivity first = activities.get(0);
        BigDecimal buyValue = BigDecimal.ZERO;
        BigDecimal sellValue = BigDecimal.ZERO;
        Set<java.time.LocalDate> buyDates = new HashSet<>();
        Set<java.time.LocalDate> sellDates = new HashSet<>();

        for (ParticipantDealActivity activity : activities) {
            if ("BUY".equals(activity.buySell())) {
                buyValue = buyValue.add(activity.value());
                buyDates.add(activity.dealDate());
            } else {
                sellValue = sellValue.add(activity.value());
                sellDates.add(activity.dealDate());
            }
        }

        BigDecimal matchedRoundTripValue = TWO.multiply(buyValue.min(sellValue));
        double churnRatio = churnRatio(matchedRoundTripValue, buyValue.add(sellValue));
        ChurnState churnState = bandChurn(churnRatio);
        RepeatBehavior repeatBehavior = classifyRepeatBehavior(buyDates.size(), sellDates.size());

        return new ParticipantFlow(
            first.participantId(), first.canonicalName(), first.participantType(), first.participantConfidence(),
            buyValue, sellValue, matchedRoundTripValue, churnRatio, churnState,
            buyDates.size(), sellDates.size(), repeatBehavior
        );
    }

    private static RepeatBehavior classifyRepeatBehavior(int buySessions, int sellSessions) {
        if (buySessions >= 3 && buySessions > sellSessions) {
            return RepeatBehavior.PERSISTENT_BUYER;
        }
        if (buySessions >= 2 && buySessions > sellSessions) {
            return RepeatBehavior.REPEAT_BUYER;
        }
        if (sellSessions >= 3 && sellSessions > buySessions) {
            return RepeatBehavior.PERSISTENT_SELLER;
        }
        if (sellSessions >= 2 && sellSessions > buySessions) {
            return RepeatBehavior.REPEAT_SELLER;
        }
        return RepeatBehavior.ONE_OFF;
    }

    private static double churnRatio(BigDecimal matchedRoundTripValue, BigDecimal grossValue) {
        if (grossValue.signum() == 0) {
            return 0.0;
        }
        return matchedRoundTripValue.divide(grossValue, 6, RoundingMode.HALF_UP).doubleValue();
    }

    /** Thresholds from the original spec: {@code <0.30 DIRECTIONAL, 0.30-0.60 MIXED, 0.60-0.80 HIGH_CHURN, >=0.80 VERY_HIGH_CHURN}. */
    static ChurnState bandChurn(double churnRatio) {
        if (churnRatio >= 0.80) {
            return ChurnState.VERY_HIGH_CHURN;
        }
        if (churnRatio >= 0.60) {
            return ChurnState.HIGH_CHURN;
        }
        if (churnRatio >= 0.30) {
            return ChurnState.MIXED;
        }
        return ChurnState.DIRECTIONAL;
    }

    private static boolean isInstitutional(ParticipantType type) {
        return type == ParticipantType.MUTUAL_FUND || type == ParticipantType.INSURANCE
            || type == ParticipantType.FPI_FII || type == ParticipantType.SOVEREIGN_PENSION_FUND
            || type == ParticipantType.AIF;
    }

    private static boolean isPropType(ParticipantType type) {
        return type == ParticipantType.PROP_DESK || type == ParticipantType.QUANT_HFT || type == ParticipantType.BROKER;
    }

    private static List<ParticipantFlow> institutional(List<ParticipantFlow> flows) {
        return flows.stream().filter(f -> isInstitutional(f.participantType())).toList();
    }

    private static List<ParticipantFlow> propType(List<ParticipantFlow> flows) {
        return flows.stream().filter(f -> isPropType(f.participantType())).toList();
    }

    private static BigDecimal sum(List<ParticipantFlow> flows, java.util.function.Function<ParticipantFlow, BigDecimal> extractor) {
        return flows.stream().map(extractor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Value-weighted (by matched round-trip value) average classification confidence - used to gate the PROP_CHURN escalation so a low-confidence guess can't manufacture the more specific label. */
    private static double weightedConfidence(List<ParticipantFlow> flows) {
        BigDecimal totalWeight = sum(flows, ParticipantFlow::matchedRoundTripValue);
        if (totalWeight.signum() == 0) {
            return 0.0;
        }
        BigDecimal weightedSum = flows.stream()
            .map(f -> f.matchedRoundTripValue().multiply(BigDecimal.valueOf(f.participantConfidence())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return weightedSum.divide(totalWeight, 6, RoundingMode.HALF_UP).doubleValue();
    }
}
