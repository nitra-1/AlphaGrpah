package com.alphagraph.discovery.convergence;

import com.alphagraph.common.rules.RuleSet;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class DiscoveryConvergenceEngineTest {

    private static final UUID INSTRUMENT_ID = UUID.randomUUID();
    private static final String SYMBOL = "RELIANCE";
    private static final LocalDate ASOF = LocalDate.of(2026, 9, 23);
    private static final RuleSet DEFAULT_RULES = new RuleSet(1, List.of());

    private final DiscoveryConvergenceEngine engine = new DiscoveryConvergenceEngine();

    // ---- evaluateDomainContribution: representative sequence drives strength, never a sum ----

    @Test
    void multipleQualifyingSequencesInOneDomainStillCountAsOneDomainForBreadth() {
        List<SequenceRow> sequences = List.of(
            seq("SEQ_A", SequencePhase.FORMING, 40.0, 80.0, ASOF.minusDays(20), ASOF.minusDays(10), ASOF),
            seq("SEQ_B", SequencePhase.PROGRESSING, 60.0, 85.0, ASOF.minusDays(15), ASOF.minusDays(5), ASOF),
            seq("SEQ_C", SequencePhase.COMPLETE, 90.0, 95.0, ASOF.minusDays(12), ASOF.minusDays(2), ASOF)
        );

        DomainContribution dc = engine.evaluateDomainContribution(ConvergenceDomain.FINANCIAL, sequences, readyReadiness(), ASOF, DEFAULT_RULES);

        assertThat(dc.status()).isEqualTo(DomainContributionStatus.ACTIVE);
        assertThat(dc.activeSequenceCount()).isEqualTo(3);
        // representative is SEQ_C (strength 90, max) + bonus for 2 extra qualifying sequences (5*2=10, capped at 10) = 100
        assertThat(dc.domainStrength()).isCloseTo(100.0, offset(0.01));
        assertThat(dc.strongestPhase()).isEqualTo(SequencePhase.COMPLETE);
    }

    @Test
    void brokenSequencesNeverCountTowardBreadthOrConfidence() {
        List<SequenceRow> sequences = List.of(seq("SEQ_A", SequencePhase.BROKEN, 90.0, 90.0, ASOF.minusDays(20), ASOF.minusDays(10), ASOF));

        DomainContribution dc = engine.evaluateDomainContribution(ConvergenceDomain.MARKET, sequences, readyReadiness(), ASOF, DEFAULT_RULES);

        assertThat(dc.status()).isEqualTo(DomainContributionStatus.BROKEN);
        assertThat(dc.activeSequenceCount()).isEqualTo(0);
        assertThat(dc.domainStrength()).isNull();
    }

    @Test
    void noSequenceRowsAtAllIsNoActiveSequence() {
        DomainContribution dc = engine.evaluateDomainContribution(ConvergenceDomain.SECTOR, List.of(), readyReadiness(), ASOF, DEFAULT_RULES);

        assertThat(dc.status()).isEqualTo(DomainContributionStatus.NO_ACTIVE_SEQUENCE);
    }

    @Test
    void unusableSourceReadinessIsInsufficientDataRegardlessOfSequences() {
        List<SequenceRow> sequences = List.of(seq("SEQ_A", SequencePhase.COMPLETE, 90.0, 90.0, ASOF.minusDays(5), ASOF.minusDays(1), ASOF));

        DomainContribution dc = engine.evaluateDomainContribution(ConvergenceDomain.OWNERSHIP, sequences, Optional.empty(), ASOF, DEFAULT_RULES);

        assertThat(dc.status()).isEqualTo(DomainContributionStatus.INSUFFICIENT_DATA);
    }

    // ---- freshness: last_step_date drives it, never as_of_date/computed_at (round-3 guard) ----

    @Test
    void freshnessBoundary_exactlyAtMaxAgeIsStillActive() {
        // stage4-market-max-age-days default is 30
        List<SequenceRow> sequences = List.of(seq("SEQ_A", SequencePhase.COMPLETE, 80.0, 90.0, ASOF.minusDays(40), ASOF.minusDays(30), ASOF));

        DomainContribution dc = engine.evaluateDomainContribution(ConvergenceDomain.MARKET, sequences, readyReadiness(), ASOF, DEFAULT_RULES);

        assertThat(dc.status()).isEqualTo(DomainContributionStatus.ACTIVE);
    }

    @Test
    void freshnessBoundary_oneDayPastMaxAgeIsStale() {
        List<SequenceRow> sequences = List.of(seq("SEQ_A", SequencePhase.COMPLETE, 80.0, 90.0, ASOF.minusDays(41), ASOF.minusDays(31), ASOF));

        DomainContribution dc = engine.evaluateDomainContribution(ConvergenceDomain.MARKET, sequences, readyReadiness(), ASOF, DEFAULT_RULES);

        assertThat(dc.status()).isEqualTo(DomainContributionStatus.STALE);
    }

    /** Round-3 addendum: as_of_date on the row itself is same-day, but last_step_date is 6+ months old - must resolve STALE, never ACTIVE, and must not anchor recency to the row's own as_of_date. */
    @Test
    void staleRowReEvaluatedTodayIsStillStaleBasedOnLastStepDateNotAsOfDate() {
        LocalDate longAgo = LocalDate.of(2026, 3, 1); // well past Market's 30-day window from ASOF (23-Sep)
        List<SequenceRow> sequences = List.of(seq("SEQ_A", SequencePhase.COMPLETE, 80.0, 90.0, longAgo.minusDays(10), longAgo, ASOF));

        DomainContribution dc = engine.evaluateDomainContribution(ConvergenceDomain.MARKET, sequences, readyReadiness(), ASOF, DEFAULT_RULES);

        assertThat(dc.status()).isEqualTo(DomainContributionStatus.STALE);
        assertThat(dc.latestSequenceDate()).isEqualTo(longAgo); // last_step_date, never ASOF
    }

    // ---- breadth transitions ----

    @Test
    void breadthTransitions_bandScoresAndJoinedReasons() {
        assertBreadth(0, 0.0, null);
        assertBreadth(1, 20.0, null);
        assertBreadth(2, 45.0, "SECOND_DOMAIN_JOINED");
        assertBreadth(3, 70.0, "THIRD_DOMAIN_JOINED");
        assertBreadth(4, 90.0, "FOURTH_DOMAIN_JOINED");
        assertBreadth(5, 100.0, "FIFTH_DOMAIN_JOINED");
    }

    private void assertBreadth(int activeCount, double expectedBreadth, String expectedReasonCode) {
        List<DomainContribution> contributions = fiveDomainsWithNActive(activeCount);
        var result = engine.evaluateConvergence(INSTRUMENT_ID, SYMBOL, ASOF, contributions, Optional.empty(), DEFAULT_RULES);

        assertThat(result.breadthScore()).as("breadth for %d active domains", activeCount).isCloseTo(expectedBreadth, offset(0.01));
        if (expectedReasonCode != null) {
            assertThat(result.reasons()).extracting("code").contains(expectedReasonCode);
        }
    }

    // ---- span gate ----

    @Test
    void spanWithinWindowReachesMultiDomainInflection() {
        List<DomainContribution> contributions = threeActiveDomainsWithSpan(30); // well within 180-day default window

        var result = engine.evaluateConvergence(INSTRUMENT_ID, SYMBOL, ASOF, contributions, Optional.empty(), DEFAULT_RULES);

        assertThat(result.preContradictionState()).isEqualTo(PreContradictionState.MULTI_DOMAIN_INFLECTION);
    }

    @Test
    void spanExceedingWindowCapsAtEarlyConvergenceEvenWithQualifyingScore() {
        List<DomainContribution> contributions = threeActiveDomainsWithSpan(200); // exceeds 180-day default window

        var result = engine.evaluateConvergence(INSTRUMENT_ID, SYMBOL, ASOF, contributions, Optional.empty(), DEFAULT_RULES);

        assertThat(result.preContradictionState()).isEqualTo(PreContradictionState.EARLY_CONVERGENCE);
    }

    // ---- readiness tiers ----

    @Test
    void zeroUsableDomainsIsInsufficientDataWithNullScores() {
        List<DomainContribution> contributions = List.of(
            insufficientData(ConvergenceDomain.FINANCIAL), insufficientData(ConvergenceDomain.OWNERSHIP), insufficientData(ConvergenceDomain.MARKET),
            insufficientData(ConvergenceDomain.SECTOR), insufficientData(ConvergenceDomain.CAPITAL_ALLOCATION)
        );

        var result = engine.evaluateConvergence(INSTRUMENT_ID, SYMBOL, ASOF, contributions, Optional.empty(), DEFAULT_RULES);

        assertThat(result.readiness()).isEqualTo(ConvergenceReadiness.INSUFFICIENT_DATA);
        assertThat(result.convergenceState()).isEqualTo(ConvergenceState.NO_CONVERGENCE);
        assertThat(result.breadthScore()).isNull();
        assertThat(result.maturityScore()).isNull();
        assertThat(result.recencyScore()).isNull();
        assertThat(result.confidenceScore()).isNull();
        assertThat(result.densityScore()).isNull();
        assertThat(result.rawConvergenceScore()).isNull();
        assertThat(result.contradictionPenalty()).isNull();
        assertThat(result.convergenceScore()).isNull();
        assertThat(result.reasons()).extracting("code").contains("DOMAIN_DATA_INSUFFICIENT");
    }

    @Test
    void onePresentDomainIsPartialDataWithRealButCappedScores() {
        List<DomainContribution> contributions = new ArrayList<>(List.of(
            noActiveSequence(ConvergenceDomain.OWNERSHIP), insufficientData(ConvergenceDomain.MARKET),
            insufficientData(ConvergenceDomain.SECTOR), insufficientData(ConvergenceDomain.CAPITAL_ALLOCATION)
        ));
        contributions.add(0, activeContribution(ConvergenceDomain.FINANCIAL, 90.0, 90.0, SequencePhase.COMPLETE, ASOF.minusDays(10), ASOF.minusDays(2)));

        var result = engine.evaluateConvergence(INSTRUMENT_ID, SYMBOL, ASOF, contributions, Optional.empty(), DEFAULT_RULES);

        assertThat(result.readiness()).isEqualTo(ConvergenceReadiness.PARTIAL_DATA);
        assertThat(result.breadthScore()).isNotNull(); // real, provisional score computed
        // structurally barred from the mature states regardless of how high the raw score would be
        assertThat(result.preContradictionState()).isIn(PreContradictionState.NO_CONVERGENCE, PreContradictionState.EARLY_CONVERGENCE);
    }

    @Test
    void threeOrMoreUsableDomainsIsReady() {
        List<DomainContribution> contributions = fiveDomainsWithNActive(3);

        var result = engine.evaluateConvergence(INSTRUMENT_ID, SYMBOL, ASOF, contributions, Optional.empty(), DEFAULT_RULES);

        assertThat(result.readiness()).isEqualTo(ConvergenceReadiness.READY);
    }

    // ---- round-2 correction 1: no convergence to contradict ----

    @Test
    void noConvergencePlusLiveContradictionStaysNoConvergence() {
        List<DomainContribution> contributions = fiveDomainsWithNActive(0);
        ContradictionOverlayRow overlay = new ContradictionOverlayRow("GROWTH_QUALITY_CONTRADICTION", 85.0, ASOF.minusDays(5), List.of("REVENUE_UP_MARGIN_DOWN"));

        var result = engine.evaluateConvergence(INSTRUMENT_ID, SYMBOL, ASOF, contributions, Optional.of(overlay), DEFAULT_RULES);

        assertThat(result.preContradictionState()).isEqualTo(PreContradictionState.NO_CONVERGENCE);
        assertThat(result.convergenceState()).isEqualTo(ConvergenceState.NO_CONVERGENCE);
        assertThat(result.reasons()).extracting("code").contains("CONTRADICTION_PRESENT");
    }

    @Test
    void positiveConvergencePlusContradictionOverlaysDisplayStateOnly() {
        List<DomainContribution> contributions = threeActiveDomainsWithSpan(20);
        ContradictionOverlayRow overlay = new ContradictionOverlayRow("GROWTH_QUALITY_CONTRADICTION", 85.0, ASOF.minusDays(5), List.of("REVENUE_UP_MARGIN_DOWN"));

        var result = engine.evaluateConvergence(INSTRUMENT_ID, SYMBOL, ASOF, contributions, Optional.of(overlay), DEFAULT_RULES);

        assertThat(result.preContradictionState()).isEqualTo(PreContradictionState.MULTI_DOMAIN_INFLECTION); // unchanged by the overlay
        assertThat(result.convergenceState()).isEqualTo(ConvergenceState.CONVERGENCE_WITH_CONTRADICTIONS);
        assertThat(result.contradictionPenalty()).isCloseTo(12.0, offset(0.01)); // GROWTH_QUALITY bucket
        assertThat(result.rawConvergenceScore()).isNotEqualTo(result.convergenceScore());
        assertThat(result.convergenceScore()).isCloseTo(result.rawConvergenceScore() - 12.0, offset(0.01));
    }

    // ---- round-2 correction 3: risk contradiction freshness ----

    @Test
    void contradictionExactlyAtFreshnessBoundaryStillPenalizes() {
        // stage4-risk-contradiction-max-age-days default is 90
        ContradictionOverlayRow overlay = new ContradictionOverlayRow("OWNERSHIP_CONTRADICTION", 80.0, ASOF.minusDays(90), List.of("OWNERSHIP_CONTRADICTION"));

        var result = engine.evaluateConvergence(INSTRUMENT_ID, SYMBOL, ASOF, threeActiveDomainsWithSpan(20), Optional.of(overlay), DEFAULT_RULES);

        assertThat(result.contradictionPenalty()).isCloseTo(8.0, offset(0.01));
    }

    @Test
    void contradictionOneDayPastFreshnessBoundaryIsZeroedButRecordedAsStale() {
        ContradictionOverlayRow overlay = new ContradictionOverlayRow("OWNERSHIP_CONTRADICTION", 80.0, ASOF.minusDays(91), List.of("OWNERSHIP_CONTRADICTION"));

        var result = engine.evaluateConvergence(INSTRUMENT_ID, SYMBOL, ASOF, threeActiveDomainsWithSpan(20), Optional.of(overlay), DEFAULT_RULES);

        assertThat(result.contradictionPenalty()).isCloseTo(0.0, offset(0.01));
        assertThat(result.reasons()).extracting("code").contains("STALE_CONTRADICTION_PRESENT");
        assertThat(result.preContradictionState()).isEqualTo(PreContradictionState.MULTI_DOMAIN_INFLECTION); // stale contradiction never blocks the mature state on its own
    }

    // ---- no-double-penalty for MULTI_DOMAIN_CONTRADICTION ----

    @Test
    void multiDomainContradictionSumsOnlyTheDistinctFiredBucketsNeverAFlatExtraPenalty() {
        ContradictionOverlayRow overlay = new ContradictionOverlayRow(
            "MULTI_DOMAIN_CONTRADICTION", 70.0, ASOF.minusDays(5),
            List.of("REVENUE_UP_MARGIN_DOWN", "OWNERSHIP_CONTRADICTION", "MULTIPLE_CONTRADICTIONS")
        );

        var result = engine.evaluateConvergence(INSTRUMENT_ID, SYMBOL, ASOF, threeActiveDomainsWithSpan(20), Optional.of(overlay), DEFAULT_RULES);

        // GROWTH_QUALITY (12) + OWNERSHIP (8) = 20, never 20 (a flat MULTI_DOMAIN penalty) + 12 + 8
        assertThat(result.contradictionPenalty()).isCloseTo(20.0, offset(0.01));
        assertThat(result.reasons()).extracting("code").contains("MULTI_DOMAIN_CONTRADICTION_PRESENT");
    }

    @Test
    void contradictionPenaltyClampsAtConfiguredMaximum() {
        // 4 buckets would sum to 12+8+6+12=38, clamped at the default max of 30
        ContradictionOverlayRow overlay = new ContradictionOverlayRow(
            "MULTI_DOMAIN_CONTRADICTION", 70.0, ASOF.minusDays(5),
            List.of("REVENUE_UP_MARGIN_DOWN", "OWNERSHIP_CONTRADICTION", "PRICE_UP_DELIVERY_FLAT_OR_DOWN", "EQUITY_RAISE_NO_GROWTH_SIGNAL")
        );

        var result = engine.evaluateConvergence(INSTRUMENT_ID, SYMBOL, ASOF, threeActiveDomainsWithSpan(20), Optional.of(overlay), DEFAULT_RULES);

        assertThat(result.contradictionPenalty()).isCloseTo(30.0, offset(0.01));
    }

    @Test
    void noClearSignalIsNeverTreatedAsAContradiction() {
        ContradictionOverlayRow overlay = new ContradictionOverlayRow("NO_CLEAR_SIGNAL", 70.0, ASOF.minusDays(1), List.of());

        var result = engine.evaluateConvergence(INSTRUMENT_ID, SYMBOL, ASOF, threeActiveDomainsWithSpan(20), Optional.of(overlay), DEFAULT_RULES);

        assertThat(result.contradictionPenalty()).isCloseTo(0.0, offset(0.01));
        assertThat(result.reasons()).extracting("code").doesNotContain("CONTRADICTION_PRESENT");
    }

    // ---- rule version ----

    @Test
    void ruleVersionIsStamped() {
        var result = engine.evaluateConvergence(INSTRUMENT_ID, SYMBOL, ASOF, fiveDomainsWithNActive(0), Optional.empty(), DEFAULT_RULES);

        assertThat(result.ruleVersion()).isEqualTo(1);
    }

    // ---- helpers ----

    private static SequenceRow seq(String type, SequencePhase phase, double strength, double confidence, LocalDate firstStep, LocalDate lastStep, LocalDate asOfDate) {
        int totalSteps = 2;
        int currentStep = phase == SequencePhase.COMPLETE ? totalSteps : 1;
        return new SequenceRow(type, phase, currentStep, totalSteps, firstStep, lastStep, strength, confidence, asOfDate);
    }

    private static Optional<ReadinessRow> readyReadiness() {
        return Optional.of(new ReadinessRow(SourceReadiness.READY, 100));
    }

    /** Mirrors DiscoveryConvergenceEngine's own private PHASE_FACTOR map - kept in sync manually since the engine doesn't expose it. */
    private static double phaseFactor(SequencePhase phase) {
        return switch (phase) {
            case FORMING -> 0.40;
            case PROGRESSING -> 0.70;
            case COMPLETE -> 1.00;
            case BROKEN -> 0.0;
        };
    }

    private static DomainContribution activeContribution(ConvergenceDomain domain, double strength, double confidence, SequencePhase phase, LocalDate earliest, LocalDate latest) {
        SequenceContribution sc = new SequenceContribution("SEQ_TYPE", phase, strength, confidence, earliest, latest, strength * phaseFactor(phase) * (confidence / 100.0));
        return new DomainContribution(domain, DomainContributionStatus.ACTIVE, 1, phase, strength, confidence, earliest, latest, strength * phaseFactor(phase), List.of(sc));
    }

    private static DomainContribution noActiveSequence(ConvergenceDomain domain) {
        return new DomainContribution(domain, DomainContributionStatus.NO_ACTIVE_SEQUENCE, 0, null, null, null, null, null, null, List.of());
    }

    private static DomainContribution insufficientData(ConvergenceDomain domain) {
        return new DomainContribution(domain, DomainContributionStatus.INSUFFICIENT_DATA, 0, null, null, null, null, null, null, List.of());
    }

    /** 5 domains, the first {@code activeCount} of them real and ACTIVE (strength/confidence high enough to matter), the rest present-but-empty (NO_ACTIVE_SEQUENCE, still counted toward coverage/READY). */
    private static List<DomainContribution> fiveDomainsWithNActive(int activeCount) {
        ConvergenceDomain[] domains = ConvergenceDomain.values();
        List<DomainContribution> result = new ArrayList<>();
        for (int i = 0; i < domains.length; i++) {
            if (i < activeCount) {
                result.add(activeContribution(domains[i], 80.0, 90.0, SequencePhase.COMPLETE, ASOF.minusDays(10), ASOF.minusDays(2)));
            } else {
                result.add(noActiveSequence(domains[i]));
            }
        }
        return result;
    }

    /**
     * 3 ACTIVE domains (clears stage4-min-domains-multidomain=3) with real strength/confidence
     * hand-tuned to a raw score of ~69.5 - comfortably clears stage4-multidomain-score-threshold
     * (55) but deliberately stays below stage4-strong-convergence-score-threshold (75), so these
     * tests isolate the MULTI_DOMAIN_INFLECTION boundary without also qualifying for
     * STRONG_CONVERGENCE. Spread across the given day span.
     */
    private static List<DomainContribution> threeActiveDomainsWithSpan(int spanDays) {
        return List.of(
            activeContribution(ConvergenceDomain.FINANCIAL, 60.0, 80.0, SequencePhase.COMPLETE, ASOF.minusDays(spanDays), ASOF.minusDays(spanDays)),
            activeContribution(ConvergenceDomain.OWNERSHIP, 55.0, 80.0, SequencePhase.COMPLETE, ASOF.minusDays(spanDays / 2), ASOF.minusDays(spanDays / 2)),
            activeContribution(ConvergenceDomain.MARKET, 50.0, 80.0, SequencePhase.PROGRESSING, ASOF, ASOF),
            noActiveSequence(ConvergenceDomain.SECTOR),
            noActiveSequence(ConvergenceDomain.CAPITAL_ALLOCATION)
        );
    }
}
