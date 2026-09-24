package com.alphagraph.discovery.lifecycle;

import com.alphagraph.common.rules.RuleSet;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * Pure calculation - no JDBC, no I/O - same convention as every Stage 2/3/4 engine. Classifies an
 * instrument's transformation *trajectory* from Stage 4's own convergence-snapshot history, never
 * another scoring layer and never independently re-derived from Stage 3. Deliberately never
 * answers BUY/SELL/TARGET_PRICE/EXPECTED_RETURN/MULTIBAGGER_PROBABILITY/SUCCESS_PROBABILITY.
 *
 * <p><b>Readiness gate, three layers, checked before anything else.</b> Layer -1: a Stage 4
 * snapshot must exist for the *exact* evaluation date - if Stage 4's own job failed or was skipped
 * today, `history`'s most-recent row will be from an earlier date, and this must never be silently
 * treated as "today's" state (reason {@code CURRENT_CONVERGENCE_SNAPSHOT_MISSING}, distinct from
 * plain insufficient history). Layer 0: that snapshot's own {@code readiness} must be {@code READY}
 * - a long historical run of {@code READY} observations never overrides a today that has
 * regressed to {@code INSUFFICIENT_DATA}/{@code PARTIAL_DATA}. Layer 1: only once both hold does
 * historical depth (`stage5-min-ready-observations` count, `stage5-min-history-span-days` span)
 * get checked at all. Any layer failing returns {@code lifecycleState = null} - never a fake
 * {@code DORMANT} for thin history, mirroring the exact discipline Stage 4 itself established for
 * {@code INSUFFICIENT_DATA}.
 *
 * <p><b>{@code previousLifecycle} is always the latest *authoritative* Stage 5 classification</b>
 * ({@code readiness=READY}, {@code lifecycle_state} non-null) - see {@link LifecycleSnapshotReader}.
 * Stage 5 legitimately writes {@code INSUFFICIENT_HISTORY}/{@code NULL} rows during temporary data
 * gaps; a naive "yesterday's row" lookup would let one bad day erase real lifecycle history for
 * hysteresis, deterioration-eligibility, and cycle-peak tracking.
 *
 * <p><b>Cycle-peak tracking, not just yesterday's raw state, drives {@code DETERIORATING}
 * eligibility.</b> {@code peakLifecycleStage} is the highest-ranked state reached during the
 * *current continuous cycle* - ranked only over the 5 genuinely-progressed states
 * ({@code EARLY_INFLECTION(1) < EMERGING(2) < ACCELERATING(3) < MARKET_RECOGNITION(4) <
 * MATURE_RERATING(5)}; {@code DORMANT}/{@code DETERIORATING} are deliberately unranked).
 * {@code MAX(previousPeak, rank(newState))} on a ranked state; unchanged on {@code DETERIORATING}
 * (decline neither raises nor erases the cycle's achievement); reset to {@code null} only on a
 * genuine return to {@code DORMANT}. This is what lets a real
 * {@code EMERGING → DETERIORATING → EARLY_INFLECTION(partial recovery) → weakens again} sequence
 * stay eligible for {@code DETERIORATING} throughout - a company that was always
 * {@code NO_CONVERGENCE}/{@code DORMANT} (peak stays {@code null}) can never reach that branch at
 * all, by construction.
 *
 * <p><b>Persistence is a structural/categorical count, not a re-derived velocity check at every
 * historical point</b> (a disclosed v1 simplification): each persistence-gated state has its own
 * structural qualifying condition (e.g. EMERGING's is "{@code preContradictionState ==
 * MULTI_DOMAIN_INFLECTION} and {@code activeDomainCount >= 3}"), counted backward from the latest
 * ready observation via {@link #countConsecutiveFromLatest}. Velocity/trend (used for {@code
 * ACCELERATING}'s entry gate and {@code DETERIORATING}'s dimension count) is computed once, from
 * the *current* window deltas, not recomputed at every historical point - simpler, and sufficient
 * to keep "one snapshot never equals one classification" true for every state.
 */
@Component
class DiscoveryLifecycleEngine {

    private static final int RULE_VERSION = 1;
    private static final int DETERIORATION_MIN_DIMENSIONS = 2;
    private static final double CONTRADICTION_RISING_THRESHOLD = 5.0;

    /** Cycle-peak rank - DORMANT and DETERIORATING are deliberately absent (neither is ever a "peak"). */
    private static final Map<LifecycleState, Integer> PEAK_RANK = Map.of(
        LifecycleState.EARLY_INFLECTION, 1, LifecycleState.EMERGING, 2, LifecycleState.ACCELERATING, 3,
        LifecycleState.MARKET_RECOGNITION, 4, LifecycleState.MATURE_RERATING, 5
    );

    private static final Map<String, Integer> CONVERGENCE_STATE_RANK = Map.of(
        "NO_CONVERGENCE", 0, "EARLY_CONVERGENCE", 1, "MULTI_DOMAIN_INFLECTION", 2, "STRONG_CONVERGENCE", 3
    );

    LifecycleResult evaluate(
        UUID instrumentId, String symbol, LocalDate asOfDate,
        List<ConvergenceSnapshotRow> history, Optional<LifecycleSnapshotRow> previousLifecycle, RuleSet rules
    ) {
        // ---- Layer -1: a Stage 4 snapshot for the exact evaluation date must exist ----
        if (history.isEmpty()) {
            return gatedResult(instrumentId, symbol, asOfDate, LifecycleReadiness.INSUFFICIENT_HISTORY, "CURRENT_CONVERGENCE_SNAPSHOT_MISSING");
        }
        ConvergenceSnapshotRow current = history.get(history.size() - 1);
        if (!current.asOfDate().equals(asOfDate)) {
            return gatedResult(instrumentId, symbol, asOfDate, LifecycleReadiness.INSUFFICIENT_HISTORY, "CURRENT_CONVERGENCE_SNAPSHOT_MISSING");
        }

        // ---- Layer 0: today's own snapshot must itself be READY ----
        if (!current.isReady()) {
            LifecycleReadiness readiness = "INSUFFICIENT_DATA".equals(current.readiness())
                ? LifecycleReadiness.INSUFFICIENT_HISTORY : LifecycleReadiness.PARTIAL_HISTORY;
            return gatedResult(instrumentId, symbol, asOfDate, readiness, "INSUFFICIENT_STAGE4_HISTORY");
        }

        // ---- Layer 1: historical depth ----
        List<ConvergenceSnapshotRow> readySnapshots = history.stream().filter(ConvergenceSnapshotRow::isReady).toList();
        int minReadyObservations = (int) DiscoveryLifecycleRuleSetLoader.ruleThreshold(rules, "stage5-min-ready-observations", 10);
        if (readySnapshots.size() < minReadyObservations) {
            return gatedResult(instrumentId, symbol, asOfDate, LifecycleReadiness.INSUFFICIENT_HISTORY, "INSUFFICIENT_STAGE4_HISTORY");
        }
        long spanDays = ChronoUnit.DAYS.between(readySnapshots.get(0).asOfDate(), readySnapshots.get(readySnapshots.size() - 1).asOfDate());
        int minHistorySpanDays = (int) DiscoveryLifecycleRuleSetLoader.ruleThreshold(rules, "stage5-min-history-span-days", 14);
        if (spanDays < minHistorySpanDays) {
            return gatedResult(instrumentId, symbol, asOfDate, LifecycleReadiness.PARTIAL_HISTORY, "INSUFFICIENT_STAGE4_HISTORY");
        }

        // ---- Trajectory features (deltas) ----
        int shortWindow = (int) DiscoveryLifecycleRuleSetLoader.ruleThreshold(rules, "stage5-short-window-observations", 5);
        int mediumWindow = (int) DiscoveryLifecycleRuleSetLoader.ruleThreshold(rules, "stage5-medium-window-observations", 10);

        double shortAvg = windowAverage(readySnapshots, shortWindow, r -> r.convergenceScore());
        double mediumAvg = windowAverage(readySnapshots, mediumWindow, r -> r.convergenceScore());
        double shortDelta = current.convergenceScore() - shortAvg;
        double mediumDelta = shortAvg - mediumAvg;
        double activeDomainDelta = current.activeDomainCount() - windowAverage(readySnapshots, shortWindow, r -> (double) r.activeDomainCount());
        double maturityDelta = current.maturityScore() - windowAverage(readySnapshots, shortWindow, r -> r.maturityScore());
        double breadthDelta = current.breadthScore() - windowAverage(readySnapshots, shortWindow, r -> r.breadthScore());
        double contradictionDelta = orZero(current.contradictionPenalty()) - windowAverage(readySnapshots, shortWindow, r -> orZero(r.contradictionPenalty()));

        // ---- Trajectory direction ----
        double risingScoreDelta = DiscoveryLifecycleRuleSetLoader.ruleThreshold(rules, "stage5-rising-score-delta", 8);
        double weakeningScoreDelta = DiscoveryLifecycleRuleSetLoader.ruleThreshold(rules, "stage5-weakening-score-delta", -8);
        double breadthContractionMinDelta = DiscoveryLifecycleRuleSetLoader.ruleThreshold(rules, "stage5-breadth-contraction-min-delta", -1);

        TrajectoryDirection direction;
        if (shortDelta >= risingScoreDelta && breadthDelta > breadthContractionMinDelta) {
            direction = TrajectoryDirection.RISING;
        } else if (shortDelta <= weakeningScoreDelta || activeDomainDelta <= breadthContractionMinDelta) {
            direction = TrajectoryDirection.WEAKENING;
        } else {
            boolean wasWeakeningOrDeteriorating = previousLifecycle
                .map(p -> p.trajectoryDirection() == TrajectoryDirection.WEAKENING || p.lifecycleState() == LifecycleState.DETERIORATING)
                .orElse(false);
            direction = (wasWeakeningOrDeteriorating && shortDelta > 0) ? TrajectoryDirection.RECOVERING : TrajectoryDirection.STABLE;
        }

        // ---- Trajectory score (0-100, 50=stable) - internal scaling constants, not rules, same tradeoff as Stage 4's own DOMAIN_BONUS_PER_EXTRA_SEQUENCE ----
        double trajectoryScore = clamp(50 + shortDelta * 1.5 + activeDomainDelta * 5.0 - contradictionDelta * 0.5, 0, 100);

        // ---- Decision hierarchy, DETERIORATING tested first ----
        Optional<LifecycleState> previousPeak = previousLifecycle.map(LifecycleSnapshotRow::peakLifecycleStage);
        List<LifecycleReason> reasons = new ArrayList<>();
        LifecycleState newState = classify(
            current, readySnapshots, previousLifecycle, previousPeak, direction, trajectoryScore,
            shortDelta, activeDomainDelta, contradictionDelta, weakeningScoreDelta, breadthContractionMinDelta,
            rules, reasons
        );

        // ---- Lifecycle strength ----
        int requiredPersistence = requiredPersistenceFor(newState, rules);
        int actualPersistence = countConsecutiveFromLatest(readySnapshots, structuralConditionFor(newState, rules));
        double persistenceScore = requiredPersistence <= 0 ? 100.0 : Math.min(100.0, 100.0 * actualPersistence / requiredPersistence);
        double confidenceScore = orZero(current.confidenceScore());
        double consistencyScore = clamp(100 - Math.abs(shortDelta - mediumDelta) * 2, 0, 100);
        double coverageByCount = Math.min(100.0, 100.0 * readySnapshots.size() / minReadyObservations);
        double coverageBySpan = Math.min(100.0, 100.0 * spanDays / minHistorySpanDays);
        double historyQualityScore = (coverageByCount + coverageBySpan) / 2.0;

        double weightPersistence = DiscoveryLifecycleRuleSetLoader.ruleThreshold(rules, "stage5-weight-persistence", 35) / 100.0;
        double weightConfidence = DiscoveryLifecycleRuleSetLoader.ruleThreshold(rules, "stage5-weight-evidence-confidence", 25) / 100.0;
        double weightConsistency = DiscoveryLifecycleRuleSetLoader.ruleThreshold(rules, "stage5-weight-trajectory-consistency", 20) / 100.0;
        double weightHistoryQuality = DiscoveryLifecycleRuleSetLoader.ruleThreshold(rules, "stage5-weight-history-quality", 20) / 100.0;
        double lifecycleStrength = clamp(
            weightPersistence * persistenceScore + weightConfidence * confidenceScore
                + weightConsistency * consistencyScore + weightHistoryQuality * historyQualityScore,
            0, 100
        );

        // ---- Date / peak tracking ----
        boolean stateChangedFromPrevious = previousLifecycle.map(p -> p.lifecycleState() != newState).orElse(true);
        LocalDate stateStartedDate = stateChangedFromPrevious ? asOfDate : previousLifecycle.get().stateStartedDate();

        LocalDate lifecycleStartedDate;
        if (newState == LifecycleState.DORMANT) {
            lifecycleStartedDate = null;
        } else if (previousLifecycle.isEmpty() || previousLifecycle.get().lifecycleState() == LifecycleState.DORMANT
            || previousLifecycle.get().lifecycleStartedDate() == null) {
            lifecycleStartedDate = asOfDate;
        } else {
            lifecycleStartedDate = previousLifecycle.get().lifecycleStartedDate();
        }
        int lifecycleAgeDays = lifecycleStartedDate == null ? 0 : (int) ChronoUnit.DAYS.between(lifecycleStartedDate, asOfDate);

        LifecycleState newPeak;
        if (newState == LifecycleState.DORMANT) {
            newPeak = null;
        } else if (newState == LifecycleState.DETERIORATING) {
            newPeak = previousPeak.orElse(null);
        } else {
            newPeak = previousPeak.filter(p -> PEAK_RANK.get(p) >= PEAK_RANK.get(newState)).orElse(newState);
        }

        double peakConvergenceScore = newState == LifecycleState.DORMANT
            ? current.convergenceScore()
            : Math.max(previousLifecycle.map(p -> orZero(p.peakConvergenceScore())).orElse(0.0), current.convergenceScore());
        int peakActiveDomains = newState == LifecycleState.DORMANT
            ? current.activeDomainCount()
            : Math.max(previousLifecycle.map(p -> p.peakActiveDomains() == null ? 0 : p.peakActiveDomains()).orElse(0), current.activeDomainCount());

        return new LifecycleResult(
            instrumentId, symbol, asOfDate, newState, LifecycleReadiness.READY,
            lifecycleStartedDate, stateStartedDate, lifecycleAgeDays, newPeak,
            lifecycleStrength, trajectoryScore, current.convergenceScore(), peakConvergenceScore,
            current.activeDomainCount(), peakActiveDomains, direction, RULE_VERSION, reasons
        );
    }

    /** Deliberately refuses to run - see {@code DiscoveryLifecycleOrchestrator}'s own javadoc for the reason. */

    private LifecycleState classify(
        ConvergenceSnapshotRow current, List<ConvergenceSnapshotRow> readySnapshots,
        Optional<LifecycleSnapshotRow> previousLifecycle, Optional<LifecycleState> previousPeak,
        TrajectoryDirection direction, double trajectoryScore,
        double shortDelta, double activeDomainDelta, double contradictionDelta,
        double weakeningScoreDelta, double breadthContractionMinDelta,
        RuleSet rules, List<LifecycleReason> reasons
    ) {
        int currentRank = CONVERGENCE_STATE_RANK.getOrDefault(current.preContradictionState(), 0);

        // ---- DETERIORATING (tested first - a stale positive label must never survive real decline) ----
        boolean peakEligible = previousPeak.map(p -> PEAK_RANK.get(p) >= PEAK_RANK.get(LifecycleState.EMERGING)).orElse(false);
        if (peakEligible) {
            int dimensionCount = 0;
            if (activeDomainDelta <= breadthContractionMinDelta) dimensionCount++;
            if (shortDelta <= weakeningScoreDelta) dimensionCount++;
            if (steppedDown(readySnapshots, currentRank)) dimensionCount++;
            if (aDomainWentInactive(readySnapshots)) dimensionCount++;
            if (contradictionDelta > CONTRADICTION_RISING_THRESHOLD) dimensionCount++;

            int deteriorationMinPersistence = (int) DiscoveryLifecycleRuleSetLoader.ruleThreshold(rules, "stage5-deterioration-min-persistence", 3);
            int deteriorationPersistence = countConsecutiveFromLatest(readySnapshots, deteriorationDayOverDayCondition(readySnapshots));
            if (dimensionCount >= DETERIORATION_MIN_DIMENSIONS && deteriorationPersistence >= deteriorationMinPersistence) {
                reasons.add(LifecycleReason.forMetric("LIFECYCLE_DETERIORATION_DETECTED", "dimensionCount", dimensionCount));
                return LifecycleState.DETERIORATING;
            }
        }

        // ---- MATURE_RERATING ----
        boolean sustainingMature = previousLifecycle.map(p -> p.lifecycleState() == LifecycleState.MATURE_RERATING).orElse(false);
        boolean fromMarketRecognition = previousLifecycle.map(p -> p.lifecycleState() == LifecycleState.MARKET_RECOGNITION).orElse(false);
        if ((sustainingMature || fromMarketRecognition) && currentRank >= 2) {
            DomainContributionRow market = current.domain("MARKET");
            boolean marketActive = market != null && market.isActive();
            double accelerationEntry = DiscoveryLifecycleRuleSetLoader.ruleThreshold(rules, "stage5-acceleration-entry-threshold", 65);
            double matureRerationMinDays = DiscoveryLifecycleRuleSetLoader.ruleThreshold(rules, "stage5-mature-rerating-min-days", 60);
            LocalDate stateStart = previousLifecycle.map(LifecycleSnapshotRow::stateStartedDate).orElse(null);
            long durationDays = stateStart == null ? 0 : ChronoUnit.DAYS.between(stateStart, current.asOfDate());
            if (marketActive && durationDays >= matureRerationMinDays && trajectoryScore < accelerationEntry) {
                reasons.add(LifecycleReason.of("MATURE_RERATING_DURATION_REACHED"));
                return LifecycleState.MATURE_RERATING;
            }
        }

        // ---- MARKET_RECOGNITION ----
        boolean fromRecognitionEligibleState = previousLifecycle.map(p ->
            p.lifecycleState() == LifecycleState.EMERGING || p.lifecycleState() == LifecycleState.ACCELERATING
                || p.lifecycleState() == LifecycleState.MARKET_RECOGNITION
        ).orElse(false);
        if (fromRecognitionEligibleState && currentRank >= 2) {
            DomainContributionRow market = current.domain("MARKET");
            double minMarketContribution = DiscoveryLifecycleRuleSetLoader.ruleThreshold(rules, "stage5-market-recognition-min-market-contribution", 60);
            int minPersistence = (int) DiscoveryLifecycleRuleSetLoader.ruleThreshold(rules, "stage5-market-recognition-min-persistence", 3);
            boolean marketQualifies = market != null && market.isActive() && orZero(market.contributionScore()) >= minMarketContribution;
            int persistence = countConsecutiveFromLatest(readySnapshots, marketRecognitionCondition(rules));
            if (marketQualifies && persistence >= minPersistence) {
                if ("MARKET_RECOGNITION_SEQUENCE".equals(market.evidenceReference())) {
                    reasons.add(LifecycleReason.withEvidence("MARKET_RECOGNITION_PERSISTED", "MARKET_RECOGNITION_SEQUENCE", current.asOfDate()));
                } else {
                    reasons.add(LifecycleReason.of("MARKET_RECOGNITION_PRESENT"));
                }
                return LifecycleState.MARKET_RECOGNITION;
            }
        }

        // ---- ACCELERATING (hysteresis: entry threshold to enter, lower exit threshold to remain) ----
        boolean alreadyAccelerating = previousLifecycle.map(p -> p.lifecycleState() == LifecycleState.ACCELERATING).orElse(false);
        double accelerationEntry = DiscoveryLifecycleRuleSetLoader.ruleThreshold(rules, "stage5-acceleration-entry-threshold", 65);
        double accelerationExit = DiscoveryLifecycleRuleSetLoader.ruleThreshold(rules, "stage5-acceleration-exit-threshold", 55);
        double effectiveThreshold = alreadyAccelerating ? accelerationExit : accelerationEntry;
        if (currentRank >= 2 && trajectoryScore >= effectiveThreshold) {
            boolean anyDeltaPositive = shortDelta > 0 || activeDomainDelta > 0;
            int minPersistence = (int) DiscoveryLifecycleRuleSetLoader.ruleThreshold(rules, "stage5-accelerating-min-persistence", 3);
            int persistence = countConsecutiveFromLatest(readySnapshots, r -> CONVERGENCE_STATE_RANK.getOrDefault(r.preContradictionState(), 0) >= 2);
            if (anyDeltaPositive && persistence >= minPersistence) {
                reasons.add(LifecycleReason.forMetric("LIFECYCLE_ACCELERATION_DETECTED", "trajectoryScore", trajectoryScore));
                return LifecycleState.ACCELERATING;
            }
        }

        // ---- EMERGING ----
        int emergingMinPersistence = (int) DiscoveryLifecycleRuleSetLoader.ruleThreshold(rules, "stage5-emerging-min-persistence", 3);
        Predicate<ConvergenceSnapshotRow> emergingCondition = r -> "MULTI_DOMAIN_INFLECTION".equals(r.preContradictionState()) && r.activeDomainCount() >= 3;
        if (emergingCondition.test(current) && direction != TrajectoryDirection.WEAKENING
            && countConsecutiveFromLatest(readySnapshots, emergingCondition) >= emergingMinPersistence) {
            reasons.add(LifecycleReason.of("MULTI_DOMAIN_INFLECTION_PERSISTED"));
            return LifecycleState.EMERGING;
        }

        // ---- EARLY_INFLECTION ----
        int earlyMinPersistence = (int) DiscoveryLifecycleRuleSetLoader.ruleThreshold(rules, "stage5-early-inflection-min-persistence", 2);
        Predicate<ConvergenceSnapshotRow> earlyCondition = r -> "EARLY_CONVERGENCE".equals(r.preContradictionState()) && r.activeDomainCount() >= 2;
        if (earlyCondition.test(current) && countConsecutiveFromLatest(readySnapshots, earlyCondition) >= earlyMinPersistence) {
            reasons.add(LifecycleReason.of("EARLY_CONVERGENCE_PERSISTED"));
            return LifecycleState.EARLY_INFLECTION;
        }

        // ---- DORMANT ----
        int dormantMinObservations = (int) DiscoveryLifecycleRuleSetLoader.ruleThreshold(rules, "stage5-dormant-min-observations", 10);
        Predicate<ConvergenceSnapshotRow> dormantCondition = r -> r.activeDomainCount() < 2;
        if (dormantCondition.test(current) && countConsecutiveFromLatest(readySnapshots, dormantCondition) >= dormantMinObservations) {
            reasons.add(LifecycleReason.of("READY_HISTORY_ESTABLISHED"));
            return LifecycleState.DORMANT;
        }

        // ---- Fallback: hysteresis remain-in, else EARLY_INFLECTION ----
        // Never re-derive DORMANT here from a single day's activeDomainCount - DORMANT is only
        // ever reachable through its own branch above, which requires real persistence. A history
        // that hasn't (yet) earned any state's persistence bar defaults to EARLY_INFLECTION - the
        // weakest positive classification, never a shortcut past DORMANT's own proof-of-absence
        // requirement.
        if (previousLifecycle.isPresent()) {
            return previousLifecycle.get().lifecycleState();
        }
        return LifecycleState.EARLY_INFLECTION;
    }

    private int requiredPersistenceFor(LifecycleState state, RuleSet rules) {
        return switch (state) {
            case EARLY_INFLECTION -> (int) DiscoveryLifecycleRuleSetLoader.ruleThreshold(rules, "stage5-early-inflection-min-persistence", 2);
            case EMERGING -> (int) DiscoveryLifecycleRuleSetLoader.ruleThreshold(rules, "stage5-emerging-min-persistence", 3);
            case ACCELERATING -> (int) DiscoveryLifecycleRuleSetLoader.ruleThreshold(rules, "stage5-accelerating-min-persistence", 3);
            case MARKET_RECOGNITION -> (int) DiscoveryLifecycleRuleSetLoader.ruleThreshold(rules, "stage5-market-recognition-min-persistence", 3);
            case DETERIORATING -> (int) DiscoveryLifecycleRuleSetLoader.ruleThreshold(rules, "stage5-deterioration-min-persistence", 3);
            case DORMANT -> (int) DiscoveryLifecycleRuleSetLoader.ruleThreshold(rules, "stage5-dormant-min-observations", 10);
            case MATURE_RERATING -> 0; // duration-gated, not observation-count-gated
        };
    }

    private Predicate<ConvergenceSnapshotRow> structuralConditionFor(LifecycleState state, RuleSet rules) {
        return switch (state) {
            case EARLY_INFLECTION -> r -> "EARLY_CONVERGENCE".equals(r.preContradictionState()) && r.activeDomainCount() >= 2;
            case EMERGING -> r -> "MULTI_DOMAIN_INFLECTION".equals(r.preContradictionState()) && r.activeDomainCount() >= 3;
            case ACCELERATING -> r -> CONVERGENCE_STATE_RANK.getOrDefault(r.preContradictionState(), 0) >= 2;
            case MARKET_RECOGNITION -> marketRecognitionCondition(rules);
            case DORMANT -> r -> r.activeDomainCount() < 2;
            case DETERIORATING, MATURE_RERATING -> r -> true; // persistence handled separately (day-over-day / duration)
        };
    }

    private Predicate<ConvergenceSnapshotRow> marketRecognitionCondition(RuleSet rules) {
        double minMarketContribution = DiscoveryLifecycleRuleSetLoader.ruleThreshold(rules, "stage5-market-recognition-min-market-contribution", 60);
        return r -> {
            DomainContributionRow market = r.domain("MARKET");
            return market != null && market.isActive() && orZero(market.contributionScore()) >= minMarketContribution;
        };
    }

    /**
     * Day-over-day decline proxy for "weakening persists" - a simpler, disclosed v1 stand-in for
     * recomputing full rolling deltas at every historical point. Precomputes the set of declining
     * dates by real list index (never {@code List.indexOf(row)}, which relies on record equality
     * and could misidentify the wrong row if two snapshots happen to share identical field values).
     */
    private Predicate<ConvergenceSnapshotRow> deteriorationDayOverDayCondition(List<ConvergenceSnapshotRow> readySnapshotsAscending) {
        Set<LocalDate> decliningDates = new HashSet<>();
        for (int i = 1; i < readySnapshotsAscending.size(); i++) {
            ConvergenceSnapshotRow row = readySnapshotsAscending.get(i);
            ConvergenceSnapshotRow previous = readySnapshotsAscending.get(i - 1);
            if (row.convergenceScore() < previous.convergenceScore() || row.activeDomainCount() < previous.activeDomainCount()) {
                decliningDates.add(row.asOfDate());
            }
        }
        return row -> decliningDates.contains(row.asOfDate());
    }

    private boolean steppedDown(List<ConvergenceSnapshotRow> readySnapshotsAscending, int currentRank) {
        int shortWindowIndex = readySnapshotsAscending.size() - 1 - 5;
        if (shortWindowIndex < 0) {
            return false;
        }
        int pastRank = CONVERGENCE_STATE_RANK.getOrDefault(readySnapshotsAscending.get(shortWindowIndex).preContradictionState(), 0);
        return currentRank < pastRank;
    }

    private boolean aDomainWentInactive(List<ConvergenceSnapshotRow> readySnapshotsAscending) {
        int shortWindowIndex = readySnapshotsAscending.size() - 1 - 5;
        if (shortWindowIndex < 0) {
            return false;
        }
        ConvergenceSnapshotRow past = readySnapshotsAscending.get(shortWindowIndex);
        ConvergenceSnapshotRow latest = readySnapshotsAscending.get(readySnapshotsAscending.size() - 1);
        for (DomainContributionRow pastDomain : past.domainContributions()) {
            if (pastDomain.isActive()) {
                DomainContributionRow latestDomain = latest.domain(pastDomain.domain());
                if (latestDomain == null || !latestDomain.isActive()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static double windowAverage(List<ConvergenceSnapshotRow> readySnapshotsAscending, int windowSize, ToDoubleFunction<ConvergenceSnapshotRow> extractor) {
        int from = Math.max(0, readySnapshotsAscending.size() - windowSize);
        List<ConvergenceSnapshotRow> window = readySnapshotsAscending.subList(from, readySnapshotsAscending.size());
        return window.stream().mapToDouble(extractor).average().orElse(0.0);
    }

    private static int countConsecutiveFromLatest(List<ConvergenceSnapshotRow> readySnapshotsAscending, Predicate<ConvergenceSnapshotRow> condition) {
        int count = 0;
        for (int i = readySnapshotsAscending.size() - 1; i >= 0; i--) {
            if (!condition.test(readySnapshotsAscending.get(i))) {
                break;
            }
            count++;
        }
        return count;
    }

    private static double orZero(Double value) {
        return value == null ? 0.0 : value;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static LifecycleResult gatedResult(UUID instrumentId, String symbol, LocalDate asOfDate, LifecycleReadiness readiness, String reasonCode) {
        return new LifecycleResult(
            instrumentId, symbol, asOfDate, null, readiness,
            null, null, null, null,
            null, null, null, null,
            null, null, TrajectoryDirection.UNKNOWN, RULE_VERSION, List.of(LifecycleReason.of(reasonCode))
        );
    }
}
