package com.alphagraph.discovery.lifecycle;

import com.alphagraph.common.rules.Rule;
import com.alphagraph.common.rules.RuleCondition;
import com.alphagraph.common.rules.RuleOperator;
import com.alphagraph.common.rules.RuleSet;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoveryLifecycleEngineTest {

    private static final UUID INSTRUMENT_ID = UUID.randomUUID();
    private static final String SYMBOL = "RELIANCE";
    private static final LocalDate ASOF = LocalDate.of(2026, 9, 24);
    private static final RuleSet DEFAULT_RULES = new RuleSet(1, List.of());

    private final DiscoveryLifecycleEngine engine = new DiscoveryLifecycleEngine();

    // ---- readiness gate, layer -1: today's own snapshot must exist ----

    @Test
    void zeroSnapshotsIsInsufficientHistory() {
        var result = engine.evaluate(INSTRUMENT_ID, SYMBOL, ASOF, List.of(), Optional.empty(), DEFAULT_RULES);

        assertThat(result.readiness()).isEqualTo(LifecycleReadiness.INSUFFICIENT_HISTORY);
        assertThat(result.lifecycleState()).isNull();
        assertThat(result.trajectoryDirection()).isEqualTo(TrajectoryDirection.UNKNOWN);
        assertThat(result.reasons()).extracting("code").contains("CURRENT_CONVERGENCE_SNAPSHOT_MISSING");
    }

    /** Round-4 correction: a strong historical READY run must never be treated as "today" when Stage 4's own job failed/skipped today. */
    @Test
    void missingTodaysSnapshotIsNeverMaskedByStrongHistoricalDepth() {
        List<ConvergenceSnapshotRow> history = dailyReadySnapshots(ASOF.minusDays(1), 18, "EARLY_CONVERGENCE", 50.0, 2, twoActiveDomains());

        var result = engine.evaluate(INSTRUMENT_ID, SYMBOL, ASOF, history, Optional.empty(), DEFAULT_RULES);

        assertThat(result.lifecycleState()).isNull();
        assertThat(result.readiness()).isEqualTo(LifecycleReadiness.INSUFFICIENT_HISTORY);
        assertThat(result.reasons()).extracting("code").contains("CURRENT_CONVERGENCE_SNAPSHOT_MISSING");
    }

    // ---- readiness gate, layer 0: today's own snapshot's readiness ----

    @Test
    void todaysInsufficientDataIsInsufficientHistoryRegardlessOfHistoricalDepth() {
        List<ConvergenceSnapshotRow> history = new ArrayList<>(dailyReadySnapshots(ASOF.minusDays(1), 18, "EARLY_CONVERGENCE", 50.0, 2, twoActiveDomains()));
        history.add(notReadySnapshot(ASOF, "INSUFFICIENT_DATA"));

        var result = engine.evaluate(INSTRUMENT_ID, SYMBOL, ASOF, history, Optional.empty(), DEFAULT_RULES);

        assertThat(result.lifecycleState()).isNull();
        assertThat(result.readiness()).isEqualTo(LifecycleReadiness.INSUFFICIENT_HISTORY);
    }

    @Test
    void todaysPartialDataIsPartialHistory() {
        List<ConvergenceSnapshotRow> history = new ArrayList<>(dailyReadySnapshots(ASOF.minusDays(1), 18, "EARLY_CONVERGENCE", 50.0, 2, twoActiveDomains()));
        history.add(notReadySnapshot(ASOF, "PARTIAL_DATA"));

        var result = engine.evaluate(INSTRUMENT_ID, SYMBOL, ASOF, history, Optional.empty(), DEFAULT_RULES);

        assertThat(result.lifecycleState()).isNull();
        assertThat(result.readiness()).isEqualTo(LifecycleReadiness.PARTIAL_HISTORY);
    }

    // ---- readiness gate, layer 1: historical depth ----

    @Test
    void fewerThanMinReadyObservationsIsInsufficientHistory() {
        List<ConvergenceSnapshotRow> history = dailyReadySnapshots(ASOF, 5, "EARLY_CONVERGENCE", 50.0, 2, twoActiveDomains());

        var result = engine.evaluate(INSTRUMENT_ID, SYMBOL, ASOF, history, Optional.empty(), DEFAULT_RULES);

        assertThat(result.readiness()).isEqualTo(LifecycleReadiness.INSUFFICIENT_HISTORY);
        assertThat(result.lifecycleState()).isNull();
    }

    @Test
    void enoughObservationsButTooShortSpanIsPartialHistory() {
        // 10 consecutive daily observations span only 9 days - below the 14-day minimum
        List<ConvergenceSnapshotRow> history = dailyReadySnapshots(ASOF, 10, "EARLY_CONVERGENCE", 50.0, 2, twoActiveDomains());

        var result = engine.evaluate(INSTRUMENT_ID, SYMBOL, ASOF, history, Optional.empty(), DEFAULT_RULES);

        assertThat(result.readiness()).isEqualTo(LifecycleReadiness.PARTIAL_HISTORY);
        assertThat(result.lifecycleState()).isNull();
    }

    @Test
    void enoughObservationsAndEnoughSpanIsReady() {
        // 15 consecutive daily observations span exactly 14 days - clears both thresholds
        List<ConvergenceSnapshotRow> history = dailyReadySnapshots(ASOF, 15, "EARLY_CONVERGENCE", 50.0, 2, twoActiveDomains());

        var result = engine.evaluate(INSTRUMENT_ID, SYMBOL, ASOF, history, Optional.empty(), DEFAULT_RULES);

        assertThat(result.readiness()).isEqualTo(LifecycleReadiness.READY);
        assertThat(result.lifecycleState()).isNotNull();
    }

    // ---- DORMANT: real persistence required ----

    @Test
    void sustainedAbsenceOfBreadthClassifiesDormant() {
        List<ConvergenceSnapshotRow> history = dailyReadySnapshots(ASOF, 15, "NO_CONVERGENCE", 10.0, 1, noActiveDomains());

        var result = engine.evaluate(INSTRUMENT_ID, SYMBOL, ASOF, history, Optional.empty(), DEFAULT_RULES);

        assertThat(result.lifecycleState()).isEqualTo(LifecycleState.DORMANT);
    }

    /** Critical regression: one dormant-looking day after 14 genuinely active days must never classify DORMANT - DORMANT requires real persistence, never a single-day shortcut through the fallback. */
    @Test
    void singleDormantLookingDayNeverClassifiesDormant() {
        List<ConvergenceSnapshotRow> history = new ArrayList<>(dailyReadySnapshots(ASOF.minusDays(1), 14, "EARLY_CONVERGENCE", 50.0, 2, twoActiveDomains()));
        history.add(readySnapshot(ASOF, "NO_CONVERGENCE", 10.0, 1, noActiveDomains()));

        var result = engine.evaluate(INSTRUMENT_ID, SYMBOL, ASOF, history, Optional.empty(), DEFAULT_RULES);

        assertThat(result.lifecycleState()).isNotEqualTo(LifecycleState.DORMANT);
    }

    // ---- EMERGING: persistence + breadth requirement ----

    @Test
    void sustainedMultiDomainInflectionWithThreeActiveDomainsClassifiesEmerging() {
        List<ConvergenceSnapshotRow> history = dailyReadySnapshots(ASOF, 15, "MULTI_DOMAIN_INFLECTION", 60.0, 3, threeActiveDomains());

        var result = engine.evaluate(INSTRUMENT_ID, SYMBOL, ASOF, history, Optional.empty(), DEFAULT_RULES);

        assertThat(result.lifecycleState()).isEqualTo(LifecycleState.EMERGING);
        assertThat(result.reasons()).extracting("code").contains("MULTI_DOMAIN_INFLECTION_PERSISTED");
    }

    // ---- EARLY_INFLECTION: persistence + breadth requirement (also the base READY case) ----

    @Test
    void sustainedEarlyConvergenceWithTwoActiveDomainsClassifiesEarlyInflection() {
        List<ConvergenceSnapshotRow> history = dailyReadySnapshots(ASOF, 15, "EARLY_CONVERGENCE", 50.0, 2, twoActiveDomains());

        var result = engine.evaluate(INSTRUMENT_ID, SYMBOL, ASOF, history, Optional.empty(), DEFAULT_RULES);

        assertThat(result.lifecycleState()).isEqualTo(LifecycleState.EARLY_INFLECTION);
        assertThat(result.reasons()).extracting("code").contains("EARLY_CONVERGENCE_PERSISTED");
    }

    // ---- ACCELERATING: velocity, not level ----

    @Test
    void risingScoreAndBreadthAtMultiDomainLevelClassifiesAccelerating() {
        // 10 steady days at MULTI_DOMAIN_INFLECTION/3 domains, then 5 days rising sharply, breadth expanding to 4
        List<ConvergenceSnapshotRow> history = new ArrayList<>(dailyReadySnapshots(ASOF.minusDays(5), 10, "MULTI_DOMAIN_INFLECTION", 55.0, 3, threeActiveDomains()));
        double[] risingScores = {58, 64, 70, 76, 85};
        for (int i = 0; i < 5; i++) {
            LocalDate date = ASOF.minusDays(4 - i);
            int domains = i >= 3 ? 4 : 3;
            history.add(readySnapshot(date, "MULTI_DOMAIN_INFLECTION", risingScores[i], domains, domains == 4 ? fourActiveDomains() : threeActiveDomains()));
        }

        var result = engine.evaluate(INSTRUMENT_ID, SYMBOL, ASOF, history, Optional.empty(), DEFAULT_RULES);

        assertThat(result.lifecycleState()).isEqualTo(LifecycleState.ACCELERATING);
        assertThat(result.trajectoryDirection()).isEqualTo(TrajectoryDirection.RISING);
    }

    @Test
    void flatHighScoreNeverClassifiesAcceleratingDespiteHighLevel() {
        // steady, high, essentially flat score/breadth for the whole window - high level, no velocity
        List<ConvergenceSnapshotRow> history = new ArrayList<>(dailyReadySnapshots(ASOF.minusDays(4), 11, "MULTI_DOMAIN_INFLECTION", 82.0, 4, fourActiveDomains()));
        double[] flatScores = {82, 81, 82, 81};
        for (int i = 0; i < 4; i++) {
            history.add(readySnapshot(ASOF.minusDays(3 - i), "MULTI_DOMAIN_INFLECTION", flatScores[i], 4, fourActiveDomains()));
        }

        var result = engine.evaluate(INSTRUMENT_ID, SYMBOL, ASOF, history, Optional.empty(), DEFAULT_RULES);

        assertThat(result.lifecycleState()).isNotEqualTo(LifecycleState.ACCELERATING);
        assertThat(result.lifecycleState()).isEqualTo(LifecycleState.EMERGING);
    }

    @Test
    void hysteresisKeepsInstrumentInAcceleratingBetweenExitAndEntryThresholds() {
        // trajectoryScore ~59.6 - below the 65 entry bar but above the 55 exit bar.
        // MARKET is deliberately kept inactive here - previousLifecycle=ACCELERATING is itself one
        // of MARKET_RECOGNITION's own eligible previous states (checked earlier in the hierarchy),
        // so an active MARKET domain would win MARKET_RECOGNITION before ACCELERATING is ever reached.
        List<ConvergenceSnapshotRow> history = new ArrayList<>(dailyReadySnapshots(ASOF.minusDays(1), 14, "MULTI_DOMAIN_INFLECTION", 70.0, 4, fourActiveDomainsExcludingMarket()));
        history.add(readySnapshot(ASOF, "MULTI_DOMAIN_INFLECTION", 78.0, 4, fourActiveDomainsExcludingMarket()));
        LifecycleSnapshotRow previous = authoritativeSnapshot(ASOF.minusDays(10), LifecycleState.ACCELERATING, LifecycleState.ACCELERATING, TrajectoryDirection.STABLE);

        var result = engine.evaluate(INSTRUMENT_ID, SYMBOL, ASOF, history, Optional.of(previous), DEFAULT_RULES);

        assertThat(result.lifecycleState()).isEqualTo(LifecycleState.ACCELERATING);
    }

    @Test
    void sameTrajectoryScoreIsNotEnoughToNewlyEnterAcceleratingWithoutHysteresis() {
        // identical trajectory to the hysteresis test, but no prior ACCELERATING state - must clear the higher ENTRY bar, not just the exit bar
        List<ConvergenceSnapshotRow> history = new ArrayList<>(dailyReadySnapshots(ASOF.minusDays(1), 14, "MULTI_DOMAIN_INFLECTION", 70.0, 4, fourActiveDomains()));
        history.add(readySnapshot(ASOF, "MULTI_DOMAIN_INFLECTION", 78.0, 4, fourActiveDomains()));

        var result = engine.evaluate(INSTRUMENT_ID, SYMBOL, ASOF, history, Optional.empty(), DEFAULT_RULES);

        assertThat(result.lifecycleState()).isNotEqualTo(LifecycleState.ACCELERATING);
    }

    // ---- trajectory direction ----

    @Test
    void sustainedDeclineClassifiesWeakeningDirection() {
        List<ConvergenceSnapshotRow> history = new ArrayList<>(dailyReadySnapshots(ASOF.minusDays(5), 10, "MULTI_DOMAIN_INFLECTION", 70.0, 4, fourActiveDomains()));
        double[] decliningScores = {60, 55, 50, 45, 40};
        for (int i = 0; i < 5; i++) {
            history.add(readySnapshot(ASOF.minusDays(4 - i), "MULTI_DOMAIN_INFLECTION", decliningScores[i], 4, fourActiveDomains()));
        }

        var result = engine.evaluate(INSTRUMENT_ID, SYMBOL, ASOF, history, Optional.empty(), DEFAULT_RULES);

        assertThat(result.trajectoryDirection()).isEqualTo(TrajectoryDirection.WEAKENING);
    }

    @Test
    void weakeningFollowedByUpturnClassifiesRecovering() {
        List<ConvergenceSnapshotRow> history = new ArrayList<>(dailyReadySnapshots(ASOF.minusDays(5), 10, "MULTI_DOMAIN_INFLECTION", 50.0, 3, threeActiveDomains()));
        double[] upturnScores = {51, 52, 53, 54, 56};
        for (int i = 0; i < 5; i++) {
            history.add(readySnapshot(ASOF.minusDays(4 - i), "MULTI_DOMAIN_INFLECTION", upturnScores[i], 3, threeActiveDomains()));
        }
        LifecycleSnapshotRow previous = authoritativeSnapshot(ASOF.minusDays(10), LifecycleState.EMERGING, LifecycleState.EMERGING, TrajectoryDirection.WEAKENING);

        var result = engine.evaluate(INSTRUMENT_ID, SYMBOL, ASOF, history, Optional.of(previous), DEFAULT_RULES);

        assertThat(result.trajectoryDirection()).isEqualTo(TrajectoryDirection.RECOVERING);
    }

    // ---- MARKET_RECOGNITION: real Market participation required, not just a high score ----

    @Test
    void marketRecognitionRequiresRealMarketParticipation() {
        List<ConvergenceSnapshotRow> history = dailyReadySnapshots(ASOF, 15, "MULTI_DOMAIN_INFLECTION", 70.0, 4,
            domainsWithMarket(true, 80.0, null));
        LifecycleSnapshotRow previous = authoritativeSnapshot(ASOF.minusDays(1), LifecycleState.EMERGING, null, TrajectoryDirection.STABLE);

        var result = engine.evaluate(INSTRUMENT_ID, SYMBOL, ASOF, history, Optional.of(previous), DEFAULT_RULES);

        assertThat(result.lifecycleState()).isEqualTo(LifecycleState.MARKET_RECOGNITION);
    }

    @Test
    void highConvergenceWithoutMarketParticipationNeverClassifiesMarketRecognition() {
        List<ConvergenceSnapshotRow> history = dailyReadySnapshots(ASOF, 15, "MULTI_DOMAIN_INFLECTION", 70.0, 4,
            domainsWithMarket(false, null, null)); // MARKET is inactive
        LifecycleSnapshotRow previous = authoritativeSnapshot(ASOF.minusDays(1), LifecycleState.EMERGING, null, TrajectoryDirection.STABLE);

        var result = engine.evaluate(INSTRUMENT_ID, SYMBOL, ASOF, history, Optional.of(previous), DEFAULT_RULES);

        assertThat(result.lifecycleState()).isNotEqualTo(LifecycleState.MARKET_RECOGNITION);
    }

    /** Round-3 correction 1: the Market contribution floor is a real, named, versioned rule - not a hardcoded number. */
    @Test
    void marketRecognitionRespectsTheNamedContributionFloorRule() {
        List<ConvergenceSnapshotRow> history = dailyReadySnapshots(ASOF, 15, "MULTI_DOMAIN_INFLECTION", 70.0, 4,
            domainsWithMarket(true, 59.0, null)); // just below the default floor of 60
        LifecycleSnapshotRow previous = authoritativeSnapshot(ASOF.minusDays(1), LifecycleState.EMERGING, null, TrajectoryDirection.STABLE);

        var belowFloor = engine.evaluate(INSTRUMENT_ID, SYMBOL, ASOF, history, Optional.of(previous), DEFAULT_RULES);
        assertThat(belowFloor.lifecycleState()).isNotEqualTo(LifecycleState.MARKET_RECOGNITION);

        RuleSet lowerFloorRule = ruleSet("stage5-market-recognition-min-market-contribution", 50);
        var withLoweredFloor = engine.evaluate(INSTRUMENT_ID, SYMBOL, ASOF, history, Optional.of(previous), lowerFloorRule);
        assertThat(withLoweredFloor.lifecycleState()).isEqualTo(LifecycleState.MARKET_RECOGNITION);
    }

    // ---- MATURE_RERATING: needs real duration ----

    @Test
    void shortMarketRecognitionStreakNeverClassifiesMatureRerating() {
        List<ConvergenceSnapshotRow> history = dailyReadySnapshots(ASOF, 15, "MULTI_DOMAIN_INFLECTION", 70.0, 4,
            domainsWithMarket(true, 80.0, null));
        LifecycleSnapshotRow previous = authoritativeSnapshot(ASOF.minusDays(5), LifecycleState.MARKET_RECOGNITION, null, TrajectoryDirection.STABLE); // only 5 days in state

        var result = engine.evaluate(INSTRUMENT_ID, SYMBOL, ASOF, history, Optional.of(previous), DEFAULT_RULES);

        assertThat(result.lifecycleState()).isNotEqualTo(LifecycleState.MATURE_RERATING);
        assertThat(result.lifecycleState()).isEqualTo(LifecycleState.MARKET_RECOGNITION);
    }

    @Test
    void sixtyPlusDayMarketRecognitionStreakClassifiesMatureRerating() {
        List<ConvergenceSnapshotRow> history = dailyReadySnapshots(ASOF, 15, "MULTI_DOMAIN_INFLECTION", 70.0, 4,
            domainsWithMarket(true, 80.0, null));
        LifecycleSnapshotRow previous = authoritativeSnapshot(ASOF.minusDays(65), LifecycleState.MARKET_RECOGNITION, null, TrajectoryDirection.STABLE);

        var result = engine.evaluate(INSTRUMENT_ID, SYMBOL, ASOF, history, Optional.of(previous), DEFAULT_RULES);

        assertThat(result.lifecycleState()).isEqualTo(LifecycleState.MATURE_RERATING);
    }

    // ---- DETERIORATING: real prior progress required, never from level alone ----

    @Test
    void permanentlyDormantHistoryNeverClassifiesDeteriorating() {
        List<ConvergenceSnapshotRow> history = dailyReadySnapshots(ASOF, 15, "NO_CONVERGENCE", 10.0, 1, noActiveDomains());
        // no previousLifecycle at all - peak was never established, so DETERIORATING's own eligibility gate can never open

        var result = engine.evaluate(INSTRUMENT_ID, SYMBOL, ASOF, history, Optional.empty(), DEFAULT_RULES);

        assertThat(result.lifecycleState()).isEqualTo(LifecycleState.DORMANT);
        assertThat(result.lifecycleState()).isNotEqualTo(LifecycleState.DETERIORATING);
    }

    @Test
    void realDeclineWithEstablishedPeakClassifiesDeteriorating() {
        List<ConvergenceSnapshotRow> history = decliningHistoryWithEstablishedPeak();
        LifecycleSnapshotRow previous = authoritativeSnapshot(ASOF.minusDays(20), LifecycleState.EMERGING, LifecycleState.EMERGING, TrajectoryDirection.STABLE);

        var result = engine.evaluate(INSTRUMENT_ID, SYMBOL, ASOF, history, Optional.of(previous), DEFAULT_RULES);

        assertThat(result.lifecycleState()).isEqualTo(LifecycleState.DETERIORATING);
    }

    // ---- cycle-peak tracking (round-3 correction 2) ----

    @Test
    void peakCarriesForwardUnchangedThroughDeterioratingState() {
        List<ConvergenceSnapshotRow> history = decliningHistoryWithEstablishedPeak();
        LifecycleSnapshotRow previous = authoritativeSnapshot(ASOF.minusDays(20), LifecycleState.EMERGING, LifecycleState.EMERGING, TrajectoryDirection.STABLE);

        var result = engine.evaluate(INSTRUMENT_ID, SYMBOL, ASOF, history, Optional.of(previous), DEFAULT_RULES);

        assertThat(result.lifecycleState()).isEqualTo(LifecycleState.DETERIORATING);
        assertThat(result.peakLifecycleStage()).isEqualTo(LifecycleState.EMERGING);
    }

    @Test
    void peakResetsToNullOnGenuineReturnToDormant() {
        List<ConvergenceSnapshotRow> history = dailyReadySnapshots(ASOF, 15, "NO_CONVERGENCE", 10.0, 1, noActiveDomains());
        LifecycleSnapshotRow previous = authoritativeSnapshot(ASOF.minusDays(1), LifecycleState.DETERIORATING, LifecycleState.EMERGING, TrajectoryDirection.STABLE);

        var result = engine.evaluate(INSTRUMENT_ID, SYMBOL, ASOF, history, Optional.of(previous), DEFAULT_RULES);

        assertThat(result.lifecycleState()).isEqualTo(LifecycleState.DORMANT);
        assertThat(result.peakLifecycleStage()).isNull();
    }

    @Test
    void peakAdvancesToHigherRankedStateWhenReached() {
        List<ConvergenceSnapshotRow> history = dailyReadySnapshots(ASOF, 15, "MULTI_DOMAIN_INFLECTION", 60.0, 3, threeActiveDomains());
        LifecycleSnapshotRow previous = authoritativeSnapshot(ASOF.minusDays(1), LifecycleState.EARLY_INFLECTION, LifecycleState.EARLY_INFLECTION, TrajectoryDirection.STABLE);

        var result = engine.evaluate(INSTRUMENT_ID, SYMBOL, ASOF, history, Optional.of(previous), DEFAULT_RULES);

        assertThat(result.lifecycleState()).isEqualTo(LifecycleState.EMERGING);
        assertThat(result.peakLifecycleStage()).isEqualTo(LifecycleState.EMERGING);
    }

    // ---- rule version ----

    @Test
    void ruleVersionIsStamped() {
        List<ConvergenceSnapshotRow> history = dailyReadySnapshots(ASOF, 15, "EARLY_CONVERGENCE", 50.0, 2, twoActiveDomains());

        var result = engine.evaluate(INSTRUMENT_ID, SYMBOL, ASOF, history, Optional.empty(), DEFAULT_RULES);

        assertThat(result.ruleVersion()).isEqualTo(1);
    }

    // ---- helpers ----

    private static ConvergenceSnapshotRow readySnapshot(LocalDate asOfDate, String preState, double score, int activeDomains, List<DomainContributionRow> domains) {
        return new ConvergenceSnapshotRow(
            asOfDate, preState, preState,
            score, score, 0.0,
            activeDomains, 5,
            70.0, 70.0, 70.0, 90.0, 40.0,
            "READY", asOfDate.minusDays(10), asOfDate,
            domains
        );
    }

    private static ConvergenceSnapshotRow notReadySnapshot(LocalDate asOfDate, String readiness) {
        return new ConvergenceSnapshotRow(
            asOfDate, "NO_CONVERGENCE", null,
            null, null, null,
            0, "PARTIAL_DATA".equals(readiness) ? 2 : 0,
            null, null, null, null, null,
            readiness, null, null, List.of()
        );
    }

    /** count consecutive daily READY snapshots ending AT lastDate (ascending order, matching the reader's own contract). */
    private static List<ConvergenceSnapshotRow> dailyReadySnapshots(LocalDate lastDate, int count, String preState, double score, int activeDomains, List<DomainContributionRow> domains) {
        List<ConvergenceSnapshotRow> list = new ArrayList<>();
        for (int i = count - 1; i >= 0; i--) {
            list.add(readySnapshot(lastDate.minusDays(i), preState, score, activeDomains, domains));
        }
        return list;
    }

    /** 10 steady MULTI_DOMAIN_INFLECTION/4-domain days (ASOF-14..ASOF-5) followed by 5 declining EARLY_CONVERGENCE days (ASOF-4..ASOF) with shrinking breadth - real, multi-dimension decline, not a flat/dormant fixture. */
    private static List<ConvergenceSnapshotRow> decliningHistoryWithEstablishedPeak() {
        List<ConvergenceSnapshotRow> history = new ArrayList<>(dailyReadySnapshots(ASOF.minusDays(5), 10, "MULTI_DOMAIN_INFLECTION", 70.0, 4, fourActiveDomains()));
        double[] decliningScores = {65, 58, 50, 42, 35};
        int[] decliningDomains = {3, 3, 2, 2, 1};
        for (int i = 0; i < 5; i++) {
            history.add(readySnapshot(ASOF.minusDays(4 - i), "EARLY_CONVERGENCE", decliningScores[i], decliningDomains[i], twoActiveDomains()));
        }
        return history;
    }

    private static DomainContributionRow activeDomain(String name) {
        return new DomainContributionRow(name, "ACTIVE", "COMPLETE", 80.0, 90.0, 80.0, null);
    }

    private static DomainContributionRow inactiveDomain(String name) {
        return new DomainContributionRow(name, "NO_ACTIVE_SEQUENCE", null, null, null, null, null);
    }

    private static List<DomainContributionRow> noActiveDomains() {
        return List.of(inactiveDomain("FINANCIAL"), inactiveDomain("OWNERSHIP"), inactiveDomain("MARKET"), inactiveDomain("SECTOR"), inactiveDomain("CAPITAL_ALLOCATION"));
    }

    private static List<DomainContributionRow> twoActiveDomains() {
        return List.of(activeDomain("FINANCIAL"), activeDomain("OWNERSHIP"), inactiveDomain("MARKET"), inactiveDomain("SECTOR"), inactiveDomain("CAPITAL_ALLOCATION"));
    }

    private static List<DomainContributionRow> threeActiveDomains() {
        return List.of(activeDomain("FINANCIAL"), activeDomain("OWNERSHIP"), activeDomain("MARKET"), inactiveDomain("SECTOR"), inactiveDomain("CAPITAL_ALLOCATION"));
    }

    private static List<DomainContributionRow> fourActiveDomains() {
        return List.of(activeDomain("FINANCIAL"), activeDomain("OWNERSHIP"), activeDomain("MARKET"), activeDomain("SECTOR"), inactiveDomain("CAPITAL_ALLOCATION"));
    }

    private static List<DomainContributionRow> fourActiveDomainsExcludingMarket() {
        return List.of(activeDomain("FINANCIAL"), activeDomain("OWNERSHIP"), activeDomain("SECTOR"), activeDomain("CAPITAL_ALLOCATION"), inactiveDomain("MARKET"));
    }

    private static List<DomainContributionRow> domainsWithMarket(boolean marketActive, Double marketContributionScore, String marketEvidenceReference) {
        DomainContributionRow market = marketActive
            ? new DomainContributionRow("MARKET", "ACTIVE", "COMPLETE", 80.0, 90.0, marketContributionScore, marketEvidenceReference)
            : inactiveDomain("MARKET");
        return List.of(activeDomain("FINANCIAL"), activeDomain("OWNERSHIP"), market, activeDomain("SECTOR"), inactiveDomain("CAPITAL_ALLOCATION"));
    }

    private static LifecycleSnapshotRow authoritativeSnapshot(LocalDate stateStartedDate, LifecycleState state, LifecycleState peak, TrajectoryDirection direction) {
        return new LifecycleSnapshotRow(
            stateStartedDate, state, direction, peak,
            stateStartedDate, stateStartedDate,
            70.0, 70.0, 3, 3
        );
    }

    private static RuleSet ruleSet(String ruleName, double threshold) {
        RuleCondition condition = new RuleCondition(RuleOperator.ALWAYS, threshold, null, threshold);
        return new RuleSet(1, List.of(new Rule(ruleName, "threshold", 1, List.of(condition))));
    }
}
