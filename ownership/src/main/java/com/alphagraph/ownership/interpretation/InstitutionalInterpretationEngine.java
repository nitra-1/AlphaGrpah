package com.alphagraph.ownership.interpretation;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Assembles the final interpretation - confidence and the persisted reason-code list - from
 * everything the orchestrator has already computed (event structure, confirmation). Deliberately
 * keeps four concepts separate, never blended into one score: {@code eventStructure} (what the
 * pattern looks like), {@code institutionalState} (what it might mean), {@code
 * discoveryConfirmationState} (did subsequent market behavior support it), {@code confidence}
 * (how trustworthy the whole interpretation is).
 */
@Component
class InstitutionalInterpretationEngine {

    private static final double CONFIDENCE_WEIGHT_PARTICIPANT = 0.35;
    private static final double CONFIDENCE_WEIGHT_MATERIALITY = 0.30;
    private static final double CONFIDENCE_WEIGHT_CHURN_CLARITY = 0.15;
    private static final double CONFIDENCE_WEIGHT_BREADTH = 0.10;
    private static final double CONFIDENCE_WEIGHT_CONFIRMATION = 0.10;
    private static final double LOW_CONFIDENCE_PARTICIPANT_THRESHOLD = 50.0;

    /** Both {@link EventStructure#PROP_CHURN} and {@link EventStructure#HIGH_CHURN_ACTIVITY} map to {@link InstitutionalState#HIGH_CHURN} - the extra granularity lives in event structure, not here. */
    static InstitutionalState institutionalStateFor(EventStructure eventStructure) {
        return switch (eventStructure) {
            case PROP_CHURN, HIGH_CHURN_ACTIVITY -> InstitutionalState.HIGH_CHURN;
            case MULTI_INSTITUTION_BUYING, SINGLE_INSTITUTION_POSITION_BUILDING,
                 INSTITUTIONAL_BUYING_CANDIDATE, DIRECTIONAL_BUYING -> InstitutionalState.POSSIBLE_ACCUMULATION;
            case INSTITUTIONAL_SELLING_CANDIDATE, DIRECTIONAL_SELLING -> InstitutionalState.POSSIBLE_DISTRIBUTION;
            case MIXED_ACTIVITY -> InstitutionalState.MIXED_ACTIVITY;
            case UNRESOLVED -> InstitutionalState.NO_CLEAR_SIGNAL;
        };
    }

    InstitutionalInterpretationResult assemble(InstitutionalInterpretationInput input) {
        double confidence = confidence(input);
        List<ReasonCode> reasons = reasonCodes(input);

        DiscoveryConfirmationResult confirmation = input.confirmation();
        InterpretationReadiness readiness = input.allDealsInWindowScored()
            ? InterpretationReadiness.READY
            : InterpretationReadiness.PENDING_DATA;
        return new InstitutionalInterpretationResult(
            input.symbol(), input.asOfDate(), input.eventStructure(), input.institutionalState(),
            confirmation.state(), confirmation.frozen(), confirmation.anchorDate(), confirmation.sessionsElapsed(),
            confirmation.confirmationScore(), confirmation.priceScore(), confirmation.deliveryScore(),
            confirmation.volumeScore(), confirmation.repeatScore(), confirmation.coveragePct(),
            round2Double(confidence), input.materialityScore(), input.reportedFlowState(), input.flowSummary().churnState(),
            input.flowSummary().institutionalBuyValue(), input.flowSummary().institutionalSellValue(),
            input.flowSummary().institutionalBuyerCount(), input.flowSummary().institutionalSellerCount(),
            readiness, input.ruleVersion(), Instant.now(), reasons
        );
    }

    private double confidence(InstitutionalInterpretationInput input) {
        List<double[]> weightedScores = new ArrayList<>(); // {weight, score}

        double participantCoverage = valueWeightedParticipantConfidence(input.flowSummary());
        weightedScores.add(new double[] {CONFIDENCE_WEIGHT_PARTICIPANT, participantCoverage});

        double materialityStrength = input.materialityScore() == null ? 0.0 : input.materialityScore();
        weightedScores.add(new double[] {CONFIDENCE_WEIGHT_MATERIALITY, materialityStrength});

        weightedScores.add(new double[] {CONFIDENCE_WEIGHT_CHURN_CLARITY, churnClarity(input.flowSummary().churnRatio())});

        int distinctParticipants = input.flowSummary().participantFlows().size();
        weightedScores.add(new double[] {CONFIDENCE_WEIGHT_BREADTH, Math.min(100.0, distinctParticipants * 20.0)});

        BigDecimal coverage = input.confirmation().coveragePct();
        if (coverage != null) {
            weightedScores.add(new double[] {CONFIDENCE_WEIGHT_CONFIRMATION, coverage.doubleValue()});
        }

        double totalWeight = weightedScores.stream().mapToDouble(w -> w[0]).sum();
        if (totalWeight == 0) {
            return 0.0;
        }
        double weightedSum = weightedScores.stream().mapToDouble(w -> w[0] * w[1]).sum();
        return clamp(weightedSum / totalWeight);
    }

    private static double valueWeightedParticipantConfidence(SymbolFlowSummary flow) {
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal weightedSum = BigDecimal.ZERO;
        for (ParticipantFlow p : flow.participantFlows()) {
            BigDecimal value = p.buyValue().add(p.sellValue());
            totalValue = totalValue.add(value);
            weightedSum = weightedSum.add(value.multiply(BigDecimal.valueOf(p.participantConfidence())));
        }
        if (totalValue.signum() == 0) {
            return 0.0;
        }
        return weightedSum.divide(totalValue, 6, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Distance from the nearest *real* churn-band boundary (0.30/0.60/0.80 - the only actual
     * decision points in {@link ParticipantFlowAnalyzer#bandChurn}), scaled so a ratio well inside
     * a band reads confidently and one sitting right on a boundary reads uncertainly. Real bug
     * caught live: the range's natural extremes (0.0/1.0) used to be included as if they were
     * decision boundaries too, so a churn ratio of exactly 0.0 - the cleanest possible directional
     * case - scored as having *zero* clarity instead of the most clarity possible.
     */
    private static double churnClarity(double churnRatio) {
        double[] boundaries = {0.30, 0.60, 0.80};
        double minDistance = Double.MAX_VALUE;
        for (double boundary : boundaries) {
            minDistance = Math.min(minDistance, Math.abs(churnRatio - boundary));
        }
        return clamp(minDistance / 0.15 * 100.0);
    }

    private List<ReasonCode> reasonCodes(InstitutionalInterpretationInput input) {
        List<ReasonCode> reasons = new ArrayList<>();
        SymbolFlowSummary flow = input.flowSummary();

        if (input.materialityLevel() == MaterialityLevel.VERY_HIGH) {
            reasons.add(ReasonCode.of("VERY_HIGH_MATERIALITY", nz(input.materialityScore())));
        } else if (input.materialityLevel() == MaterialityLevel.HIGH) {
            reasons.add(ReasonCode.of("HIGH_MATERIALITY", nz(input.materialityScore())));
        }

        reasons.add(switch (flow.churnState()) {
            case DIRECTIONAL -> ReasonCode.of("LOW_CHURN", flow.churnRatio() * 100);
            case MIXED -> ReasonCode.of("MODERATE_CHURN", flow.churnRatio() * 100);
            case HIGH_CHURN -> ReasonCode.of("HIGH_CHURN", flow.churnRatio() * 100);
            case VERY_HIGH_CHURN -> ReasonCode.of("VERY_HIGH_CHURN", flow.churnRatio() * 100);
        });

        if (flow.institutionalBuyerCount() >= 2) {
            reasons.add(ReasonCode.of("MULTIPLE_INSTITUTIONAL_BUYERS", flow.institutionalBuyerCount()));
        } else if (flow.institutionalBuyerCount() == 1) {
            reasons.add(ReasonCode.of("SINGLE_INSTITUTIONAL_BUYER"));
        }
        if (flow.institutionalSellerCount() >= 2) {
            reasons.add(ReasonCode.of("MULTIPLE_INSTITUTIONAL_SELLERS", flow.institutionalSellerCount()));
        } else if (flow.institutionalSellerCount() == 1) {
            reasons.add(ReasonCode.of("SINGLE_INSTITUTIONAL_SELLER"));
        }

        for (ParticipantFlow p : flow.participantFlows()) {
            switch (p.repeatBehavior()) {
                case PERSISTENT_BUYER -> reasons.add(ReasonCode.of("PERSISTENT_BUYER_20D", p.buySessions(), p.canonicalName()));
                case REPEAT_BUYER -> reasons.add(ReasonCode.of("REPEATED_BUYING_PRE_EVENT", p.buySessions(), p.canonicalName()));
                case PERSISTENT_SELLER -> reasons.add(ReasonCode.of("PERSISTENT_SELLER_20D", p.sellSessions(), p.canonicalName()));
                case REPEAT_SELLER -> reasons.add(ReasonCode.of("REPEATED_SELLING_PRE_EVENT", p.sellSessions(), p.canonicalName()));
                case ONE_OFF -> { /* not evidence worth persisting */ }
            }
        }

        double participantCoverage = valueWeightedParticipantConfidence(flow);
        if (participantCoverage < LOW_CONFIDENCE_PARTICIPANT_THRESHOLD && !flow.participantFlows().isEmpty()) {
            reasons.add(ReasonCode.of("LOW_CONFIDENCE_PARTICIPANT_MIX", participantCoverage));
        }

        addConfirmationReasons(input, reasons);
        addCountervailingEvidenceReason(input, reasons);
        addDealPriceContextReason(input, reasons);

        return reasons;
    }

    private static void addConfirmationReasons(InstitutionalInterpretationInput input, List<ReasonCode> reasons) {
        DiscoveryConfirmationResult confirmation = input.confirmation();
        if (confirmation.priceScore() != null) {
            double score = confirmation.priceScore().doubleValue();
            if (score >= 60) {
                reasons.add(ReasonCode.of(
                    input.institutionalState() == InstitutionalState.POSSIBLE_ACCUMULATION ? "PRICE_ABOVE_DEAL_PRICE" : "PRICE_BELOW_DEAL_PRICE",
                    score
                ));
            } else if (score <= 40) {
                reasons.add(ReasonCode.of(
                    input.institutionalState() == InstitutionalState.POSSIBLE_ACCUMULATION ? "PRICE_BELOW_DEAL_PRICE" : "PRICE_ABOVE_DEAL_PRICE",
                    score
                ));
            }
        }
        if (confirmation.deliveryScore() != null) {
            double score = confirmation.deliveryScore().doubleValue();
            if (score >= 60) {
                reasons.add(ReasonCode.of("DELIVERY_TREND_SUPPORTIVE", score));
            } else if (score <= 40) {
                reasons.add(ReasonCode.of("DELIVERY_TREND_UNSUPPORTIVE", score));
            }
        }
    }

    /** A same-materiality (>=MEDIUM) deal on the opposite side of the confirming direction, after the anchor formed, that did NOT reset the anchor (see ConfirmationAnchorResolver) - real evidence worth surfacing even though it wasn't strong enough to change the event. */
    private static void addCountervailingEvidenceReason(InstitutionalInterpretationInput input, List<ReasonCode> reasons) {
        if (input.institutionalState() != InstitutionalState.POSSIBLE_ACCUMULATION
            && input.institutionalState() != InstitutionalState.POSSIBLE_DISTRIBUTION) {
            return;
        }
        String confirmingSide = input.institutionalState() == InstitutionalState.POSSIBLE_ACCUMULATION ? "BUY" : "SELL";
        String opposingSide = "BUY".equals(confirmingSide) ? "SELL" : "BUY";
        java.time.LocalDate anchor = input.confirmation().anchorDate();
        if (anchor == null) {
            return;
        }

        boolean hasCountervailing = input.windowDeals().stream()
            .anyMatch(d -> opposingSide.equals(d.buySell())
                && d.materialityLevel().atLeast(MaterialityLevel.MEDIUM)
                && !d.dealDate().isBefore(anchor));
        if (hasCountervailing) {
            reasons.add(ReasonCode.of(
                "BUY".equals(confirmingSide) ? "COUNTERVAILING_SELL_DURING_ACCUMULATION" : "COUNTERVAILING_BUY_DURING_DISTRIBUTION"
            ));
        }
    }

    /** Section 7's deal-price-context bands - the anchor's own weighted event price vs. the close immediately before it, real evidence, never itself a buy/sell signal. */
    private static void addDealPriceContextReason(InstitutionalInterpretationInput input, List<ReasonCode> reasons) {
        java.time.LocalDate anchor = input.confirmation().anchorDate();
        if (anchor == null || input.preAnchorBaselineSessions().isEmpty()) {
            return;
        }
        String confirmingSide = input.institutionalState() == InstitutionalState.POSSIBLE_ACCUMULATION ? "BUY"
            : input.institutionalState() == InstitutionalState.POSSIBLE_DISTRIBUTION ? "SELL" : null;
        if (confirmingSide == null) {
            return;
        }

        List<AnchorCandidateDeal> anchorDeals = input.windowDeals().stream()
            .filter(d -> d.dealDate().equals(anchor) && confirmingSide.equals(d.buySell()))
            .toList();
        BigDecimal totalQuantity = anchorDeals.stream().map(AnchorCandidateDeal::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalQuantity.signum() == 0) {
            return;
        }
        BigDecimal weightedPrice = anchorDeals.stream()
            .map(d -> d.price().multiply(d.quantity()))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(totalQuantity, 4, RoundingMode.HALF_UP);

        BigDecimal previousClose = input.preAnchorBaselineSessions().get(input.preAnchorBaselineSessions().size() - 1).close();
        if (previousClose.signum() == 0) {
            return;
        }
        double vsCloseePct = weightedPrice.subtract(previousClose)
            .divide(previousClose, 6, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100)).doubleValue();

        String band;
        if (vsCloseePct <= -10) {
            band = "DEEP_DISCOUNT_DEAL";
        } else if (vsCloseePct <= -3) {
            band = "DISCOUNT_DEAL";
        } else if (vsCloseePct < 3) {
            band = "NEAR_MARKET_DEAL";
        } else if (vsCloseePct < 10) {
            band = "PREMIUM_DEAL";
        } else {
            band = "HIGH_PREMIUM_DEAL";
        }
        reasons.add(ReasonCode.of(band, vsCloseePct));
    }

    private static double nz(Double value) {
        return value == null ? 0.0 : value;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }

    private static BigDecimal round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static double round2Double(double value) {
        return round2(value).doubleValue();
    }
}
