package com.alphagraph.discovery.convergence;

import com.alphagraph.common.rules.RuleSet;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Pure calculation - no JDBC, no I/O - same convention as every Stage 2/3 engine. Detects whether
 * multiple independent Stage 3 transformation domains are converging for the same instrument
 * (docs/008's own Stage 4 hand-off point). Deliberately never answers BUY/SELL, price targets,
 * multibagger probability, lifecycle stage, or anything ML-derived - a deterministic, explainable,
 * point-in-time snapshot only.
 *
 * <p><b>{@code last_step_date}, never {@code as_of_date}/{@code computed_at}, drives freshness and
 * recency everywhere.</b> A Stage 3 row's own {@code as_of_date} is merely when that row was last
 * (re-)evaluated - Stage 3 jobs keep re-writing a row's {@code as_of_date} forward even when the
 * underlying sequence hasn't genuinely advanced. {@code last_step_date} is the real evidence-
 * advancement date. A sequence that {@code COMPLETE}d in March is stale in September even if
 * Stage 3 kept re-evaluating and re-writing that row through September - the row existing later
 * never means the transformation itself advanced later.
 *
 * <p><b>Multiple sequences in one domain count as ONE domain for breadth, never summed.</b>
 * {@code domainStrength} comes from the single qualifying sequence with the highest {@code
 * sequenceStrength} (the "representative" sequence) plus a small bonus for additional qualifying
 * sequences (+5 each, capped +10) - never a sum, which would let one sequence-rich domain
 * overwhelm the detector.
 *
 * <p><b>Readiness is computed before any scoring, and {@code INSUFFICIENT_DATA} never
 * manufactures a fake zero score.</b> When fewer than 1 domain has usable Stage 3 readiness, every
 * score/penalty field is {@code null} - genuinely unevaluated, never a real zero. {@code
 * PARTIAL_DATA} (1-2 usable domains) still computes real provisional component scores, but is
 * structurally barred from {@code MULTI_DOMAIN_INFLECTION}/{@code STRONG_CONVERGENCE} (both
 * require {@code readiness == READY}) - "we don't know yet" must never look identical to "we
 * checked and it's negative."
 *
 * <p><b>{@code pre_contradiction_state} is computed strictly before, and independent of, the
 * contradiction penalty</b> - both the {@code STRONG_CONVERGENCE} and {@code MULTI_DOMAIN_INFLECTION}
 * thresholds are checked against the raw, pre-penalty score uniformly. The contradiction penalty is
 * calculated entirely separately and only applied afterward, as a display-level overlay: {@code
 * CONVERGENCE_WITH_CONTRADICTIONS} only ever replaces an actual positive {@code
 * pre_contradiction_state} ({@code EARLY_CONVERGENCE}/{@code MULTI_DOMAIN_INFLECTION}/{@code
 * STRONG_CONVERGENCE}) - {@code NO_CONVERGENCE} plus a live contradiction stays {@code
 * NO_CONVERGENCE}, since there is no convergence to contradict.
 *
 * <p><b>Risk contradictions expire too.</b> Unbounded {@code <= asOfDate} reads would let a
 * contradiction from months ago silently keep penalizing today's convergence forever if Risk
 * Stage 2 hasn't written a fresher state since. Older than {@code
 * stage4-risk-contradiction-max-age-days} -&gt; zero penalty contribution, but still recorded as
 * {@code STALE_CONTRADICTION_PRESENT} for explainability.
 *
 * <p><b>Contradiction reason-code classification, verified against the real shape</b> (not an
 * assumed array field) - {@code RiskContradictionEngine.calculate} adds a fixed marker reason per
 * trigger: {@code REVENUE_UP_MARGIN_DOWN} (growth-quality), {@code OWNERSHIP_CONTRADICTION}
 * (ownership - always present among {@code ownershipSignal.reasons()} whenever that trigger
 * fires, since that's literally the firing condition being copied), {@code
 * PRICE_UP_DELIVERY_FLAT_OR_DOWN} (price-without-delivery), {@code EQUITY_RAISE_NO_GROWTH_SIGNAL}
 * (capital-raise-weak); {@code MULTIPLE_CONTRADICTIONS} is a marker only, never itself penalized.
 * Each bucket present is penalized once (not per occurrence), summed, then clamped at {@code
 * stage4-max-contradiction-penalty} - this is what avoids double-penalizing {@code
 * MULTI_DOMAIN_CONTRADICTION} as "20 + all underlying."
 */
@Component
class DiscoveryConvergenceEngine {

    private static final int RULE_VERSION = 1;

    private static final Map<SequencePhase, Double> PHASE_FACTOR = Map.of(
        SequencePhase.FORMING, 0.40, SequencePhase.PROGRESSING, 0.70, SequencePhase.COMPLETE, 1.00, SequencePhase.BROKEN, 0.0
    );
    private static final Map<SequencePhase, Integer> PHASE_WEIGHT = Map.of(
        SequencePhase.FORMING, 1, SequencePhase.PROGRESSING, 2, SequencePhase.COMPLETE, 3, SequencePhase.BROKEN, 0
    );

    private static final double[] BREADTH_BAND = {0, 20, 45, 70, 90, 100};
    private static final double[] DENSITY_BAND = {0, 10, 25, 40, 55, 70, 80, 88, 94, 100};

    private static final double DOMAIN_BONUS_PER_EXTRA_SEQUENCE = 5.0;
    private static final double DOMAIN_BONUS_CAP = 10.0;

    private static final Map<String, String> TRIGGER_BUCKET = Map.of(
        "REVENUE_UP_MARGIN_DOWN", "GROWTH_QUALITY",
        "OWNERSHIP_CONTRADICTION", "OWNERSHIP",
        "PRICE_UP_DELIVERY_FLAT_OR_DOWN", "PRICE_WITHOUT_DELIVERY",
        "EQUITY_RAISE_NO_GROWTH_SIGNAL", "CAPITAL_RAISE_WEAK"
    );
    private static final Map<String, Double> BUCKET_PENALTY = Map.of(
        "GROWTH_QUALITY", 12.0, "OWNERSHIP", 8.0, "PRICE_WITHOUT_DELIVERY", 6.0, "CAPITAL_RAISE_WEAK", 12.0
    );

    DomainContribution evaluateDomainContribution(
        ConvergenceDomain domain, List<SequenceRow> sequences, Optional<ReadinessRow> readiness, LocalDate asOfDate, RuleSet rules
    ) {
        if (readiness.isEmpty() || readiness.get().readiness() != SourceReadiness.READY) {
            return emptyContribution(domain, DomainContributionStatus.INSUFFICIENT_DATA);
        }

        List<SequenceRow> qualifying = sequences.stream().filter(s -> s.phase() != SequencePhase.BROKEN).toList();
        if (qualifying.isEmpty()) {
            return emptyContribution(domain, sequences.isEmpty() ? DomainContributionStatus.NO_ACTIVE_SEQUENCE : DomainContributionStatus.BROKEN);
        }

        SequenceRow representative = qualifying.stream().max(Comparator.comparingDouble(SequenceRow::sequenceStrength)).orElseThrow();
        double domainStrength = clamp(representative.sequenceStrength() + Math.min(DOMAIN_BONUS_CAP, DOMAIN_BONUS_PER_EXTRA_SEQUENCE * (qualifying.size() - 1)), 0, 100);
        SequencePhase strongestPhase = representative.phase();

        double weightedConfidenceSum = 0;
        int weightTotal = 0;
        LocalDate earliestSequenceDate = null;
        LocalDate latestSequenceDate = null; // last_step_date-based - round-3 freshness guard
        List<SequenceContribution> contributions = new ArrayList<>();
        for (SequenceRow s : qualifying) {
            int weight = PHASE_WEIGHT.get(s.phase());
            weightedConfidenceSum += s.confidence() * weight;
            weightTotal += weight;
            if (s.firstStepDate() != null && (earliestSequenceDate == null || s.firstStepDate().isBefore(earliestSequenceDate))) {
                earliestSequenceDate = s.firstStepDate();
            }
            if (s.lastStepDate() != null && (latestSequenceDate == null || s.lastStepDate().isAfter(latestSequenceDate))) {
                latestSequenceDate = s.lastStepDate();
            }
            double contributionValue = s.sequenceStrength() * PHASE_FACTOR.get(s.phase()) * (s.confidence() / 100.0);
            contributions.add(new SequenceContribution(s.sequenceType(), s.phase(), s.sequenceStrength(), s.confidence(), s.firstStepDate(), s.lastStepDate(), contributionValue));
        }
        double domainConfidence = weightTotal == 0 ? 0.0 : weightedConfidenceSum / weightTotal;

        int maxAgeDays = DiscoveryConvergenceRuleSetLoader.ruleThreshold(rules, maxAgeRuleNameFor(domain), defaultMaxAgeFor(domain));
        long ageDays = latestSequenceDate == null ? Long.MAX_VALUE : ChronoUnit.DAYS.between(latestSequenceDate, asOfDate);
        DomainContributionStatus status = ageDays <= maxAgeDays ? DomainContributionStatus.ACTIVE : DomainContributionStatus.STALE;

        double contributionScore = domainStrength * PHASE_FACTOR.get(strongestPhase);

        return new DomainContribution(domain, status, qualifying.size(), strongestPhase, domainStrength, domainConfidence, earliestSequenceDate, latestSequenceDate, contributionScore, contributions);
    }

    ConvergenceResult evaluateConvergence(
        UUID instrumentId, String symbol, LocalDate asOfDate,
        List<DomainContribution> domainContributions, Optional<ContradictionOverlayRow> overlay, RuleSet rules
    ) {
        int domainCoverageCount = (int) domainContributions.stream().filter(dc -> dc.status() != DomainContributionStatus.INSUFFICIENT_DATA).count();
        int domainCoveragePct = (int) Math.round(domainCoverageCount / (double) domainContributions.size() * 100);
        ConvergenceReadiness readiness = domainCoverageCount >= 3 ? ConvergenceReadiness.READY
            : domainCoverageCount >= 1 ? ConvergenceReadiness.PARTIAL_DATA
            : ConvergenceReadiness.INSUFFICIENT_DATA;

        int qualifyingSequenceCount = domainContributions.stream().mapToInt(DomainContribution::activeSequenceCount).sum();

        List<ReasonCode> reasons = new ArrayList<>();
        for (DomainContribution dc : domainContributions) {
            if (dc.status() == DomainContributionStatus.ACTIVE) {
                Optional<SequenceContribution> rep = representative(dc);
                reasons.add(ReasonCode.domainActive(
                    dc.domain().name() + "_TRANSFORMATION_ACTIVE", dc.domain(),
                    rep.map(SequenceContribution::sequenceType).orElse(null), dc.latestSequenceDate()
                ));
            } else if (dc.status() == DomainContributionStatus.STALE) {
                reasons.add(ReasonCode.forDomain("DOMAIN_STALE", dc.domain()));
            } else if (dc.status() == DomainContributionStatus.INSUFFICIENT_DATA) {
                reasons.add(ReasonCode.forDomain("DOMAIN_DATA_INSUFFICIENT", dc.domain()));
            }
        }

        if (readiness == ConvergenceReadiness.INSUFFICIENT_DATA) {
            reasons.add(0, ReasonCode.of("DOMAIN_DATA_INSUFFICIENT"));
            return new ConvergenceResult(
                instrumentId, symbol, asOfDate,
                ConvergenceState.NO_CONVERGENCE, null,
                0, qualifyingSequenceCount, domainCoverageCount, domainCoveragePct,
                null, null, null, null, null, null, null, null,
                null, null,
                readiness, RULE_VERSION, domainContributions, reasons
            );
        }

        List<DomainContribution> activeDomains = domainContributions.stream().filter(DomainContribution::isActive).toList();
        int activeDomainCount = activeDomains.size();

        double breadthScore = BREADTH_BAND[Math.min(activeDomainCount, 5)];
        double maturityScore = activeDomains.isEmpty() ? 0.0
            : activeDomains.stream().mapToDouble(dc -> dc.domainStrength() * PHASE_FACTOR.get(dc.strongestPhase())).average().orElse(0.0);

        LocalDate earliestActive = activeDomains.stream().map(DomainContribution::earliestSequenceDate).filter(Objects::nonNull).min(LocalDate::compareTo).orElse(null);
        LocalDate latestActive = activeDomains.stream().map(DomainContribution::latestSequenceDate).filter(Objects::nonNull).max(LocalDate::compareTo).orElse(null);
        long convergenceSpanDays = (earliestActive == null || latestActive == null) ? 0 : ChronoUnit.DAYS.between(earliestActive, latestActive);
        double recencyScore = recencyBand(convergenceSpanDays);

        int inflectionDensity = domainContributions.stream().mapToInt(dc -> Math.min(2, dc.activeSequenceCount())).sum();
        double densityScore = DENSITY_BAND[Math.min(inflectionDensity, 9)];

        double weightedConfSum = 0;
        int weightTotal = 0;
        for (DomainContribution dc : activeDomains) {
            int weight = PHASE_WEIGHT.get(dc.strongestPhase());
            weightedConfSum += dc.domainConfidence() * weight;
            weightTotal += weight;
        }
        double confidenceScore = weightTotal == 0 ? 0.0 : weightedConfSum / weightTotal;

        double weightBreadth = DiscoveryConvergenceRuleSetLoader.ruleThreshold(rules, "stage4-weight-breadth", 30) / 100.0;
        double weightMaturity = DiscoveryConvergenceRuleSetLoader.ruleThreshold(rules, "stage4-weight-maturity", 25) / 100.0;
        double weightRecency = DiscoveryConvergenceRuleSetLoader.ruleThreshold(rules, "stage4-weight-recency", 20) / 100.0;
        double weightConfidence = DiscoveryConvergenceRuleSetLoader.ruleThreshold(rules, "stage4-weight-confidence", 15) / 100.0;
        double weightDensity = DiscoveryConvergenceRuleSetLoader.ruleThreshold(rules, "stage4-weight-density", 10) / 100.0;
        double rawConvergenceScore = breadthScore * weightBreadth + maturityScore * weightMaturity
            + recencyScore * weightRecency + confidenceScore * weightConfidence + densityScore * weightDensity;

        int maxSpanDays = DiscoveryConvergenceRuleSetLoader.ruleThreshold(rules, "stage4-convergence-max-span-days", 180);
        boolean spanOk = convergenceSpanDays <= maxSpanDays;
        int minEarly = DiscoveryConvergenceRuleSetLoader.ruleThreshold(rules, "stage4-min-domains-early-convergence", 2);
        int minMulti = DiscoveryConvergenceRuleSetLoader.ruleThreshold(rules, "stage4-min-domains-multidomain", 3);
        double strongThreshold = DiscoveryConvergenceRuleSetLoader.ruleThreshold(rules, "stage4-strong-convergence-score-threshold", 75);
        double multiThreshold = DiscoveryConvergenceRuleSetLoader.ruleThreshold(rules, "stage4-multidomain-score-threshold", 55);
        long maturePhaseCount = activeDomains.stream().filter(dc -> dc.strongestPhase() == SequencePhase.PROGRESSING || dc.strongestPhase() == SequencePhase.COMPLETE).count();

        PreContradictionState preContradictionState;
        if (activeDomainCount < minEarly) {
            preContradictionState = PreContradictionState.NO_CONVERGENCE;
        } else if (activeDomainCount < minMulti) {
            preContradictionState = PreContradictionState.EARLY_CONVERGENCE;
        } else if (readiness == ConvergenceReadiness.READY && maturePhaseCount >= 2 && rawConvergenceScore >= strongThreshold && spanOk) {
            preContradictionState = PreContradictionState.STRONG_CONVERGENCE;
        } else if (readiness == ConvergenceReadiness.READY && rawConvergenceScore >= multiThreshold && spanOk) {
            preContradictionState = PreContradictionState.MULTI_DOMAIN_INFLECTION;
        } else {
            preContradictionState = PreContradictionState.EARLY_CONVERGENCE;
        }

        ContradictionOutcome contradictionOutcome = evaluateContradiction(overlay, asOfDate, rules);
        double contradictionPenalty = contradictionOutcome.penalty();
        reasons.addAll(contradictionOutcome.reasons());

        double convergenceScore = clamp(rawConvergenceScore - contradictionPenalty, 0, 100);

        boolean overlayApplies = preContradictionState != PreContradictionState.NO_CONVERGENCE && contradictionPenalty > 0;
        ConvergenceState convergenceState = overlayApplies
            ? ConvergenceState.CONVERGENCE_WITH_CONTRADICTIONS
            : ConvergenceState.valueOf(preContradictionState.name());

        switch (activeDomainCount) {
            case 2 -> reasons.add(ReasonCode.of("SECOND_DOMAIN_JOINED"));
            case 3 -> reasons.add(ReasonCode.of("THIRD_DOMAIN_JOINED"));
            case 4 -> reasons.add(ReasonCode.of("FOURTH_DOMAIN_JOINED"));
            case 5 -> reasons.add(ReasonCode.of("FIFTH_DOMAIN_JOINED"));
            default -> { }
        }
        if (preContradictionState == PreContradictionState.MULTI_DOMAIN_INFLECTION) {
            reasons.add(ReasonCode.of("MULTI_DOMAIN_THRESHOLD_REACHED"));
        }
        if (preContradictionState == PreContradictionState.STRONG_CONVERGENCE) {
            reasons.add(ReasonCode.of("STRONG_CONVERGENCE_THRESHOLD_REACHED"));
        }
        if (inflectionDensity >= 6) {
            reasons.add(ReasonCode.of("HIGH_SEQUENCE_DENSITY", inflectionDensity));
        }
        if (activeDomainCount >= 2 && convergenceSpanDays <= 30) {
            reasons.add(ReasonCode.of("RECENT_CROSS_DOMAIN_ALIGNMENT", convergenceSpanDays));
        }

        return new ConvergenceResult(
            instrumentId, symbol, asOfDate,
            convergenceState, preContradictionState,
            activeDomainCount, qualifyingSequenceCount, domainCoverageCount, domainCoveragePct,
            breadthScore, maturityScore, recencyScore, confidenceScore, densityScore,
            rawConvergenceScore, contradictionPenalty, convergenceScore,
            earliestActive, latestActive,
            readiness, RULE_VERSION, domainContributions, reasons
        );
    }

    private ContradictionOutcome evaluateContradiction(Optional<ContradictionOverlayRow> overlay, LocalDate asOfDate, RuleSet rules) {
        if (overlay.isEmpty()) {
            return new ContradictionOutcome(0.0, List.of());
        }
        ContradictionOverlayRow row = overlay.get();
        if ("NO_CLEAR_SIGNAL".equals(row.primaryState())) {
            return new ContradictionOutcome(0.0, List.of());
        }

        int maxAgeDays = DiscoveryConvergenceRuleSetLoader.ruleThreshold(rules, "stage4-risk-contradiction-max-age-days", 90);
        long riskAgeDays = ChronoUnit.DAYS.between(row.asOfDate(), asOfDate);
        if (riskAgeDays > maxAgeDays) {
            return new ContradictionOutcome(0.0, List.of(ReasonCode.of("STALE_CONTRADICTION_PRESENT")));
        }

        Set<String> buckets = row.reasonCodes().stream()
            .map(TRIGGER_BUCKET::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        double sum = buckets.stream().mapToDouble(BUCKET_PENALTY::get).sum();
        int maxPenalty = DiscoveryConvergenceRuleSetLoader.ruleThreshold(rules, "stage4-max-contradiction-penalty", 30);
        double penalty = Math.min(sum, maxPenalty);

        List<ReasonCode> reasons = new ArrayList<>();
        if (penalty > 0) {
            reasons.add(ReasonCode.of("CONTRADICTION_PRESENT"));
        }
        if ("MULTI_DOMAIN_CONTRADICTION".equals(row.primaryState())) {
            reasons.add(ReasonCode.of("MULTI_DOMAIN_CONTRADICTION_PRESENT"));
        }
        return new ContradictionOutcome(penalty, reasons);
    }

    private static Optional<SequenceContribution> representative(DomainContribution dc) {
        return dc.sequences().stream().max(Comparator.comparingDouble(SequenceContribution::sequenceStrength));
    }

    private static DomainContribution emptyContribution(ConvergenceDomain domain, DomainContributionStatus status) {
        return new DomainContribution(domain, status, 0, null, null, null, null, null, null, List.of());
    }

    private static double recencyBand(long spanDays) {
        if (spanDays <= 30) return 100;
        if (spanDays <= 60) return 85;
        if (spanDays <= 90) return 70;
        if (spanDays <= 180) return 50;
        if (spanDays <= 270) return 30;
        return 0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String maxAgeRuleNameFor(ConvergenceDomain domain) {
        return switch (domain) {
            case MARKET -> "stage4-market-max-age-days";
            case SECTOR -> "stage4-sector-max-age-days";
            case FINANCIAL -> "stage4-financial-max-age-days";
            case OWNERSHIP -> "stage4-ownership-max-age-days";
            case CAPITAL_ALLOCATION -> "stage4-capital-max-age-days";
        };
    }

    private static int defaultMaxAgeFor(ConvergenceDomain domain) {
        return switch (domain) {
            case MARKET, SECTOR -> 30;
            case FINANCIAL, OWNERSHIP -> 150;
            case CAPITAL_ALLOCATION -> 180;
        };
    }

    private record ContradictionOutcome(double penalty, List<ReasonCode> reasons) {
    }
}
