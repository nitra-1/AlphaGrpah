package com.alphagraph.ownership.transformation;

import com.alphagraph.common.rules.ArithmeticRuleEvaluator;
import com.alphagraph.common.rules.EvaluationResult;
import com.alphagraph.common.rules.MetricContext;
import com.alphagraph.common.rules.Rule;
import com.alphagraph.common.rules.RuleEvaluator;
import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.ownership.api.BulkDeal;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure calculation - no JDBC, no I/O - same convention as {@code ownership.deals.DealMaterialityEngine}
 * (takes already-assembled input, returns a result; the orchestrator does all reading and writing).
 *
 * <p>For each of the 9 tracked metrics, walks to the most recent <b>non-null</b> prior observation
 * (not just the immediately preceding row - a metric can be individually null for one or more
 * quarters pending XBRL enrichment) to compute {@code change_pp}/{@code velocity_pp_per_quarter};
 * {@code persistence_quarters} counts consecutive same-sign transitions walking backward through
 * non-null observations. A metric with no prior observation at all still gets an evidence row
 * (first-ever observation, lower confidence, null change/velocity) - never skipped, matching
 * {@code financial.engine.FundamentalEngine}'s own missing-prior-period philosophy.
 *
 * <p>Bands promoter/FII/DII change into the five core states via {@link ArithmeticRuleEvaluator}
 * against the seeded {@code ownership-transformation-*} rules (a score >= {@value #SIGNAL_THRESHOLD}
 * crosses "this is a real signal, not noise"), cross-references real reported bulk/block buying for
 * {@code BULK_BUYING_WITH_OWNERSHIP_EXPANSION}, and picks one {@code primary_state} via a hardcoded
 * Java priority ladder - final categorical banding is never itself a DB rule, matching
 * {@code DealMaterialityEngine#materialityLevelFor}'s convention. Every state that actually fired is
 * recorded as a reason code regardless of which one wins the ladder, so a contradiction never hides
 * the accumulation that co-occurred with it.
 */
@Component
class OwnershipTransformationEngine {

    private static final RuleEvaluator ARITHMETIC = new ArithmeticRuleEvaluator();
    private static final double SIGNAL_THRESHOLD = 30.0;

    private static final List<TransformationState> PRIORITY_LADDER = List.of(
        TransformationState.OWNERSHIP_CONTRADICTION,
        TransformationState.BULK_BUYING_WITH_OWNERSHIP_EXPANSION,
        TransformationState.INSTITUTIONAL_OWNERSHIP_EXPANSION,
        TransformationState.FII_ACCUMULATION,
        TransformationState.DII_ACCUMULATION,
        TransformationState.PROMOTER_HOLDING_INCREASE,
        TransformationState.PROMOTER_DILUTION
    );

    Optional<TransformationCalculation> calculate(List<TransformationShareholdingPeriod> periods, List<BulkDeal> bulkDeals, RuleSet rules) {
        if (periods.isEmpty()) {
            return Optional.empty();
        }
        TransformationShareholdingPeriod current = periods.get(periods.size() - 1);

        Map<TransformationMetric, EvidenceObservation> observations = new EnumMap<>(TransformationMetric.class);
        List<EvidenceObservation> evidence = new ArrayList<>();
        for (TransformationMetric metric : TransformationMetric.values()) {
            BigDecimal currentValue = current.valueFor(metric);
            if (currentValue == null) {
                continue;
            }
            EvidenceObservation observation = computeObservation(metric, periods, current, currentValue);
            observations.put(metric, observation);
            evidence.add(observation);
        }

        OwnershipTransformationResult result = bandStates(current, observations, bulkDeals, rules);
        return Optional.of(new TransformationCalculation(evidence, result));
    }

    private EvidenceObservation computeObservation(
        TransformationMetric metric, List<TransformationShareholdingPeriod> periods,
        TransformationShareholdingPeriod current, BigDecimal currentValue
    ) {
        TransformationShareholdingPeriod priorPeriod = null;
        BigDecimal priorValue = null;
        for (int i = periods.size() - 2; i >= 0; i--) {
            BigDecimal candidate = periods.get(i).valueFor(metric);
            if (candidate != null) {
                priorPeriod = periods.get(i);
                priorValue = candidate;
                break;
            }
        }

        if (priorPeriod == null) {
            return new EvidenceObservation(
                metric, current.instrumentId(), current.symbol(), current.periodEnd(), null,
                currentValue, null, null, null, 0, 40.0
            );
        }

        BigDecimal changePp = currentValue.subtract(priorValue);
        double quartersElapsed = Math.max(1.0, quartersBetween(priorPeriod.periodEnd(), current.periodEnd()));
        BigDecimal velocity = changePp.divide(BigDecimal.valueOf(quartersElapsed), 4, RoundingMode.HALF_UP);
        int persistence = persistenceQuarters(metric, periods, changePp.signum());

        return new EvidenceObservation(
            metric, current.instrumentId(), current.symbol(), current.periodEnd(), priorPeriod.periodEnd(),
            currentValue, priorValue, changePp, velocity, persistence, 90.0
        );
    }

    /** Counts consecutive same-sign transitions between non-null observations, walking backward from the current one, including it. */
    private static int persistenceQuarters(TransformationMetric metric, List<TransformationShareholdingPeriod> periods, int currentSign) {
        if (currentSign == 0) {
            return 0;
        }
        List<BigDecimal> nonNullAscending = periods.stream().map(p -> p.valueFor(metric)).filter(Objects::nonNull).toList();
        int count = 0;
        for (int i = nonNullAscending.size() - 1; i > 0; i--) {
            int sign = nonNullAscending.get(i).subtract(nonNullAscending.get(i - 1)).signum();
            if (sign != currentSign) {
                break;
            }
            count++;
        }
        return count;
    }

    private static double quartersBetween(LocalDate from, LocalDate to) {
        return Period.between(from, to).toTotalMonths() / 3.0;
    }

    private OwnershipTransformationResult bandStates(
        TransformationShareholdingPeriod current, Map<TransformationMetric, EvidenceObservation> observations,
        List<BulkDeal> bulkDeals, RuleSet rules
    ) {
        List<ReasonCode> reasons = new ArrayList<>();
        List<TransformationState> fired = new ArrayList<>();

        Boolean promoterUp = signalDirection(observations.get(TransformationMetric.PROMOTER), rules, "ownership-transformation-promoter-change");
        Boolean fiiUp = signalDirection(observations.get(TransformationMetric.FII), rules, "ownership-transformation-fii-change");
        Boolean diiUp = signalDirection(observations.get(TransformationMetric.DII), rules, "ownership-transformation-dii-change");

        if (Boolean.TRUE.equals(promoterUp)) {
            fired.add(TransformationState.PROMOTER_HOLDING_INCREASE);
            reasons.add(ReasonCode.of("PROMOTER_HOLDING_INCREASE", observations.get(TransformationMetric.PROMOTER).changePp().doubleValue()));
        } else if (Boolean.FALSE.equals(promoterUp)) {
            fired.add(TransformationState.PROMOTER_DILUTION);
            reasons.add(ReasonCode.of("PROMOTER_DILUTION", observations.get(TransformationMetric.PROMOTER).changePp().doubleValue()));
        }
        if (Boolean.TRUE.equals(fiiUp)) {
            fired.add(TransformationState.FII_ACCUMULATION);
            reasons.add(ReasonCode.of("FII_ACCUMULATION", observations.get(TransformationMetric.FII).changePp().doubleValue()));
        }
        if (Boolean.TRUE.equals(diiUp)) {
            fired.add(TransformationState.DII_ACCUMULATION);
            reasons.add(ReasonCode.of("DII_ACCUMULATION", observations.get(TransformationMetric.DII).changePp().doubleValue()));
        }
        if (Boolean.TRUE.equals(fiiUp) && Boolean.TRUE.equals(diiUp)) {
            fired.add(TransformationState.INSTITUTIONAL_OWNERSHIP_EXPANSION);
            reasons.add(ReasonCode.of("INSTITUTIONAL_OWNERSHIP_EXPANSION"));
        }
        if ((Boolean.TRUE.equals(fiiUp) || Boolean.TRUE.equals(diiUp)) && Boolean.FALSE.equals(promoterUp)) {
            fired.add(TransformationState.OWNERSHIP_CONTRADICTION);
            reasons.add(ReasonCode.of("OWNERSHIP_CONTRADICTION"));
        }

        LocalDate priorPeriodEnd = priorPeriodEndOf(observations);
        if ((Boolean.TRUE.equals(fiiUp) || Boolean.TRUE.equals(diiUp)) && priorPeriodEnd != null) {
            BigDecimal netReportedBulkBuyValue = netReportedBulkBuyValue(bulkDeals, priorPeriodEnd, current.periodEnd());
            if (netReportedBulkBuyValue.signum() > 0) {
                fired.add(TransformationState.BULK_BUYING_WITH_OWNERSHIP_EXPANSION);
                reasons.add(ReasonCode.of("BULK_BUYING_WITH_OWNERSHIP_EXPANSION", netReportedBulkBuyValue.doubleValue()));
            }
        }

        TransformationState primaryState = PRIORITY_LADDER.stream().filter(fired::contains).findFirst().orElse(TransformationState.NO_CLEAR_SIGNAL);
        double confidence = averageConfidence(observations, TransformationMetric.PROMOTER, TransformationMetric.FII, TransformationMetric.DII);

        return new OwnershipTransformationResult(
            current.instrumentId(), current.symbol(), LocalDate.now(), current.periodEnd(), priorPeriodEnd,
            primaryState, confidence, rules.version(), Instant.now(), reasons
        );
    }

    private static LocalDate priorPeriodEndOf(Map<TransformationMetric, EvidenceObservation> observations) {
        return observations.values().stream()
            .map(EvidenceObservation::priorPeriodEnd)
            .filter(Objects::nonNull)
            .max(LocalDate::compareTo)
            .orElse(null);
    }

    /** true = up past the signal threshold, false = down past it, null = no observation, no prior, or below threshold either way. */
    private static Boolean signalDirection(EvidenceObservation observation, RuleSet rules, String ruleName) {
        if (observation == null || observation.changePp() == null) {
            return null;
        }
        double absChangePp = observation.changePp().abs().doubleValue();
        double score = evaluateExact(rules, ruleName, absChangePp);
        if (score < SIGNAL_THRESHOLD) {
            return null;
        }
        return observation.changePp().signum() > 0;
    }

    private static double evaluateExact(RuleSet rules, String ruleName, double metricValue) {
        Optional<Rule> rule = rules.rules().stream().filter(r -> r.name().equals(ruleName)).findFirst();
        if (rule.isEmpty()) {
            return 0.0;
        }
        MetricContext context = new MetricContext(Map.of("absChangePp", metricValue));
        EvaluationResult result = ARITHMETIC.evaluate(rule.get(), context);
        return result.metricPresent() ? result.contribution() : 0.0;
    }

    /**
     * Named "reported", not "institutional" - {@code ownership.bulk_deals} carries no
     * participant-type classification, so this sums every disclosed bulk/block trade in the
     * window, a real but weaker proxy (large-trade-size by regulatory definition), never a claim
     * of confirmed institutional origin.
     */
    private static BigDecimal netReportedBulkBuyValue(List<BulkDeal> bulkDeals, LocalDate afterExclusive, LocalDate throughInclusive) {
        BigDecimal net = BigDecimal.ZERO;
        for (BulkDeal deal : bulkDeals) {
            if (deal.dealDate().isAfter(afterExclusive) && !deal.dealDate().isAfter(throughInclusive)) {
                BigDecimal value = deal.price().multiply(BigDecimal.valueOf(deal.quantity()));
                net = "BUY".equals(deal.buySell()) ? net.add(value) : net.subtract(value);
            }
        }
        return net;
    }

    private static double averageConfidence(Map<TransformationMetric, EvidenceObservation> observations, TransformationMetric... metrics) {
        double sum = 0;
        int count = 0;
        for (TransformationMetric metric : metrics) {
            EvidenceObservation obs = observations.get(metric);
            if (obs != null) {
                sum += obs.confidence();
                count++;
            }
        }
        return count == 0 ? 0.0 : Math.round((sum / count) * 100.0) / 100.0;
    }
}
