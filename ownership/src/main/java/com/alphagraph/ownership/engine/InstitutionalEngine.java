package com.alphagraph.ownership.engine;

import com.alphagraph.common.engine.Engine;
import com.alphagraph.common.rules.ArithmeticRuleEvaluator;
import com.alphagraph.common.rules.EvaluationResult;
import com.alphagraph.common.rules.MetricContext;
import com.alphagraph.common.rules.Rule;
import com.alphagraph.common.rules.RuleEvaluator;
import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.ownership.api.BulkDeal;
import com.alphagraph.ownership.api.DeliveryStatus;
import com.alphagraph.ownership.api.DeliveryVolumeBar;
import com.alphagraph.ownership.api.InstitutionalBehaviour;
import com.alphagraph.ownership.api.InstitutionalEngineInput;
import com.alphagraph.ownership.api.InstitutionalFlowStatus;
import com.alphagraph.ownership.api.InstitutionalScore;
import com.alphagraph.ownership.api.PromoterStatus;
import com.alphagraph.ownership.api.ShareholdingPattern;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Answers "what is smart money doing?" from promoter/FII/DII/MF shareholding trend, delivery %,
 * volume, and real bulk/block deals. Pure with respect to already-loaded data
 * (docs/002_Engine_Architecture.md §5): {@code intelligence} assembles
 * {@link InstitutionalEngineInput} (bridging market's delivery%/volume into ownership's own
 * shareholding/bulk-deal data) and calls {@link #calculate}; this class never fetches anything
 * itself.
 *
 * The raw-score-to-0-100 {@code institutionalScore} mapping is an engine implementation choice,
 * not itself a {@code Rule} - same convention as technical.engine.TechnicalEngine (Module 1.5)
 * and financial.engine.FundamentalEngine (Module 1.6).
 */
@Component
public class InstitutionalEngine implements Engine<InstitutionalEngineInput, InstitutionalScore> {

    private static final double TREND_NOISE_BAND_PP = 0.5;
    private static final int VOLUME_LOOKBACK = 20;
    private static final int TOTAL_TRACKED_METRICS = 7;

    private final RuleEvaluator ruleEvaluator = new ArithmeticRuleEvaluator();

    @Override
    public InstitutionalScore calculate(InstitutionalEngineInput input, RuleSet rules) {
        List<ShareholdingPattern> periods = input.shareholdingPeriodsAscending();
        if (periods.isEmpty()) {
            throw new IllegalArgumentException("Cannot compute an institutional score with zero shareholding periods: " + input.symbol());
        }

        ShareholdingPattern current = periods.get(periods.size() - 1);
        Optional<ShareholdingPattern> prior = periods.size() >= 2
            ? Optional.of(periods.get(periods.size() - 2))
            : Optional.empty();

        Double promoterChangePct = changeInPoints(current.promoterPercentage(), prior.map(ShareholdingPattern::promoterPercentage));
        Double fiiChangePct = changeInPoints(current.fiiPercentage(), prior.map(ShareholdingPattern::fiiPercentage));
        Double diiChangePct = changeInPoints(current.diiPercentage(), prior.map(ShareholdingPattern::diiPercentage));
        Double mfChangePct = changeInPoints(current.mfPercentage(), prior.map(ShareholdingPattern::mfPercentage));

        Double avgDeliveryPercentage = averageDelivery(input.recentMarketActivity());
        Double relativeVolume = relativeVolume(input.recentMarketActivity());
        Long netBulkDealQuantity = netBulkDealQuantity(input.recentBulkDeals());

        double institutionalScore = scoreFromRules(
            rules, promoterChangePct, fiiChangePct, diiChangePct, mfChangePct,
            avgDeliveryPercentage, relativeVolume, netBulkDealQuantity
        );

        PromoterStatus promoterStatus = classifyPromoter(promoterChangePct);
        InstitutionalFlowStatus fiiStatus = classifyFlow(fiiChangePct);
        InstitutionalFlowStatus diiStatus = classifyFlow(diiChangePct);
        InstitutionalFlowStatus mfStatus = classifyFlow(mfChangePct);
        DeliveryStatus deliveryStatus = classifyDelivery(avgDeliveryPercentage);
        InstitutionalBehaviour behaviour = classifyBehaviour(institutionalScore);

        double confidence = confidenceFrom(
            promoterChangePct, fiiChangePct, diiChangePct, mfChangePct,
            avgDeliveryPercentage, relativeVolume, netBulkDealQuantity
        );

        LocalDate asOfDate = input.recentMarketActivity().isEmpty()
            ? current.periodEnd()
            : input.recentMarketActivity().get(input.recentMarketActivity().size() - 1).tradeDate();

        return new InstitutionalScore(
            input.instrumentId(), input.symbol(), asOfDate,
            promoterStatus, fiiStatus, diiStatus, mfStatus, deliveryStatus, behaviour,
            institutionalScore, confidence,
            toDouble(current.promoterPercentage()), promoterChangePct, fiiChangePct, diiChangePct, mfChangePct,
            avgDeliveryPercentage, relativeVolume, netBulkDealQuantity,
            rules.version(), Instant.now()
        );
    }

    private static Double changeInPoints(BigDecimal currentValue, Optional<BigDecimal> priorValue) {
        if (currentValue == null || priorValue.isEmpty() || priorValue.get() == null) {
            return null;
        }
        return currentValue.subtract(priorValue.get()).doubleValue();
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private static Double averageDelivery(List<DeliveryVolumeBar> bars) {
        List<DeliveryVolumeBar> withDelivery = bars.stream().filter(b -> b.deliveryPercentage() != null).toList();
        if (withDelivery.isEmpty()) {
            return null;
        }
        double sum = 0;
        for (DeliveryVolumeBar bar : withDelivery) {
            sum += bar.deliveryPercentage().doubleValue();
        }
        return sum / withDelivery.size();
    }

    /** Most recent day's volume divided by the average of the preceding VOLUME_LOOKBACK days. */
    private static Double relativeVolume(List<DeliveryVolumeBar> bars) {
        int n = bars.size();
        if (n < VOLUME_LOOKBACK + 1) {
            return null;
        }
        long sum = 0;
        for (int i = n - 1 - VOLUME_LOOKBACK; i < n - 1; i++) {
            sum += bars.get(i).volume();
        }
        double avg = (double) sum / VOLUME_LOOKBACK;
        if (avg == 0) {
            return null;
        }
        return bars.get(n - 1).volume() / avg;
    }

    private static Long netBulkDealQuantity(List<BulkDeal> deals) {
        if (deals.isEmpty()) {
            return null;
        }
        long net = 0;
        for (BulkDeal deal : deals) {
            net += "BUY".equals(deal.buySell()) ? deal.quantity() : -deal.quantity();
        }
        return net;
    }

    private double scoreFromRules(
        RuleSet rules, Double promoterChangePct, Double fiiChangePct, Double diiChangePct, Double mfChangePct,
        Double avgDeliveryPercentage, Double relativeVolume, Long netBulkDealQuantity
    ) {
        Map<String, Double> metrics = new HashMap<>();
        putIfPresent(metrics, "promoterChangePct", promoterChangePct);
        putIfPresent(metrics, "fiiChangePct", fiiChangePct);
        putIfPresent(metrics, "diiChangePct", diiChangePct);
        putIfPresent(metrics, "mfChangePct", mfChangePct);
        putIfPresent(metrics, "avgDeliveryPercentage", avgDeliveryPercentage);
        putIfPresent(metrics, "relativeVolume", relativeVolume);
        if (netBulkDealQuantity != null) {
            metrics.put("netBulkDealQuantity", netBulkDealQuantity.doubleValue());
        }

        MetricContext context = new MetricContext(metrics);
        double rawScore = 0;
        for (Rule rule : rules.rules()) {
            EvaluationResult result = ruleEvaluator.evaluate(rule, context);
            rawScore += result.contribution();
        }
        return clamp(50.0 + rawScore * 10.0, 0.0, 100.0);
    }

    private static void putIfPresent(Map<String, Double> metrics, String key, Double value) {
        if (value != null) {
            metrics.put(key, value);
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static PromoterStatus classifyPromoter(Double changePct) {
        if (changePct == null) {
            return PromoterStatus.STABLE;
        }
        if (changePct > TREND_NOISE_BAND_PP) {
            return PromoterStatus.ACCUMULATING;
        }
        if (changePct < -TREND_NOISE_BAND_PP) {
            return PromoterStatus.DISTRIBUTING;
        }
        return PromoterStatus.STABLE;
    }

    private static InstitutionalFlowStatus classifyFlow(Double changePct) {
        if (changePct == null) {
            return InstitutionalFlowStatus.STABLE;
        }
        if (changePct > TREND_NOISE_BAND_PP) {
            return InstitutionalFlowStatus.BUYING;
        }
        if (changePct < -TREND_NOISE_BAND_PP) {
            return InstitutionalFlowStatus.SELLING;
        }
        return InstitutionalFlowStatus.STABLE;
    }

    private static DeliveryStatus classifyDelivery(Double avgDeliveryPercentage) {
        if (avgDeliveryPercentage == null) {
            return DeliveryStatus.MODERATE;
        }
        if (avgDeliveryPercentage > 70) {
            return DeliveryStatus.VERY_HIGH;
        }
        if (avgDeliveryPercentage > 50) {
            return DeliveryStatus.HIGH;
        }
        if (avgDeliveryPercentage > 30) {
            return DeliveryStatus.MODERATE;
        }
        return DeliveryStatus.LOW;
    }

    private static InstitutionalBehaviour classifyBehaviour(double institutionalScore) {
        if (institutionalScore >= 80) {
            return InstitutionalBehaviour.STRONG_ACCUMULATION;
        }
        if (institutionalScore >= 60) {
            return InstitutionalBehaviour.ACCUMULATION;
        }
        if (institutionalScore > 40) {
            return InstitutionalBehaviour.NEUTRAL;
        }
        if (institutionalScore > 20) {
            return InstitutionalBehaviour.DISTRIBUTION;
        }
        return InstitutionalBehaviour.STRONG_DISTRIBUTION;
    }

    private static double confidenceFrom(Object... metrics) {
        long available = java.util.Arrays.stream(metrics).filter(m -> m != null).count();
        return 40.0 + 60.0 * (available / (double) TOTAL_TRACKED_METRICS);
    }
}
