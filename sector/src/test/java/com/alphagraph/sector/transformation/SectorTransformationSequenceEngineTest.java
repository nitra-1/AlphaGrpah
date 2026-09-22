package com.alphagraph.sector.transformation;

import com.alphagraph.common.rules.RuleSet;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class SectorTransformationSequenceEngineTest {

    private static final UUID INSTRUMENT_ID = UUID.randomUUID();
    private static final String SYMBOL = "RELIANCE";
    private static final LocalDate D0 = LocalDate.of(2026, 8, 1);
    private static final RuleSet DEFAULT_RULES = new RuleSet(1, List.of());

    private final SectorTransformationSequenceEngine engine = new SectorTransformationSequenceEngine();

    // ---- SECTOR_TAILWIND_SEQUENCE ----

    @Test
    void sectorRsOnlyIsForming() {
        List<SectorInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("SECTOR_RS_RISING"), sectorRs(0, 90.0, 1, true), null, null)
        );

        var result = engine.evaluateSectorTailwindSequence(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(SectorSequencePhase.FORMING);
        assertThat(result.get().currentStep()).isEqualTo(1);
    }

    @Test
    void sectorRsThenNiftyOutperformanceCompletes() {
        List<SectorInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("SECTOR_RS_RISING"), sectorRs(0, 90.0, 1, true), null, null),
            entry(1, Set.of("OUTPERFORMING_NIFTY_20D"), null, vsNifty(1, 75.0, 1, true), null)
        );

        var result = engine.evaluateSectorTailwindSequence(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(SectorSequencePhase.COMPLETE);
        assertThat(result.get().firstStepDate()).isEqualTo(D0);
        assertThat(result.get().lastStepDate()).isEqualTo(D0.plusDays(1));
    }

    @Test
    void gapExceedingMaxBreaksTheSequence() {
        // default stage3-sector-tailwind-max-gap is 15
        List<SectorInflectionHistoryEntry> history = new ArrayList<>();
        history.add(entry(0, Set.of("SECTOR_RS_RISING"), sectorRs(0, 90.0, 1, true), null, null));
        for (int i = 1; i <= 16; i++) {
            history.add(entry(i, Set.of(), null, null, null));
        }

        var result = engine.evaluateSectorTailwindSequence(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(SectorSequencePhase.BROKEN);
        assertThat(result.get().reasons()).extracting("code").contains("SEQUENCE_EXPIRED");
    }

    @Test
    void wrongOrderNeverStarts() {
        List<SectorInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("OUTPERFORMING_NIFTY_20D"), null, vsNifty(0, 75.0, 1, true), null)
        );

        var result = engine.evaluateSectorTailwindSequence(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isEmpty();
    }

    @Test
    void neverFiringProducesNoResultAtAll() {
        List<SectorInflectionHistoryEntry> history = List.of(
            entry(0, Set.of(), null, null, null),
            entry(1, Set.of(), null, null, null)
        );

        var result = engine.evaluateSectorTailwindSequence(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isEmpty();
    }

    // ---- STOCK_LEADERSHIP_EMERGENCE ----

    @Test
    void threeStepChainCompletesInOrder() {
        List<SectorInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("SECTOR_RS_RISING"), sectorRs(0, 90.0, 1, true), null, null),
            entry(1, Set.of("OUTPERFORMING_SECTOR_20D"), null, null, vsSector(1, 60.0, 1, true)),
            entry(2, Set.of("SECTOR_LEADERSHIP_CROSSING"), null, null, vsSector(2, 60.0, 0, true))
        );

        var result = engine.evaluateStockLeadershipEmergence(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(SectorSequencePhase.COMPLETE);
        assertThat(result.get().currentStep()).isEqualTo(3);
    }

    /** Decision 2: SECTOR_LEADERSHIP_CROSSING structurally co-occurs with OUTPERFORMING_SECTOR_20D on the same real observation - both steps must advance within the same iteration. */
    @Test
    void sameSessionCrossingSatisfiesStepsTwoAndThreeTogether() {
        SectorEvidenceObservation crossingObservation = vsSector(1, 60.0, 0, true);
        List<SectorInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("SECTOR_RS_RISING"), sectorRs(0, 90.0, 1, true), null, null),
            entry(1, Set.of("OUTPERFORMING_SECTOR_20D", "SECTOR_LEADERSHIP_CROSSING"), null, null, crossingObservation)
        );

        var result = engine.evaluateStockLeadershipEmergence(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(SectorSequencePhase.COMPLETE);
        assertThat(result.get().currentStep()).isEqualTo(3);
        assertThat(result.get().lastStepDate()).isEqualTo(D0.plusDays(1));
    }

    // ---- Decision 1: evidence-date guard against stale, pre-existing evidence ----

    @Test
    void staleEvidencePredatingTheAnchorDoesNotCountAsAFreshStep() {
        // OUTPERFORMING_NIFTY_20D's own real evidence (D0, stale) predates SECTOR_RS_RISING's own
        // real evidence (D0+5) - the stock was ALREADY outperforming Nifty for unrelated reasons
        // before the sector ever strengthened. This must NOT complete the sequence.
        SectorEvidenceObservation staleNifty = vsNifty(0, 75.0, 1, true); // real evidence dated D0
        List<SectorInflectionHistoryEntry> history = List.of(
            entry(5, Set.of("SECTOR_RS_RISING"), sectorRs(5, 90.0, 1, true), null, null), // anchor dated D0+5
            entry(6, Set.of("OUTPERFORMING_NIFTY_20D"), null, staleNifty, null) // row 6, but evidence still dated D0
        );

        var result = engine.evaluateSectorTailwindSequence(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        // stays at step 1 - the stale evidence was correctly rejected as a fresh completion
        assertThat(result.get().currentStep()).isEqualTo(1);
        assertThat(result.get().sequencePhase()).isNotEqualTo(SectorSequencePhase.COMPLETE);
    }

    @Test
    void freshEvidenceAtOrAfterTheAnchorDoesCompleteTheSequence() {
        List<SectorInflectionHistoryEntry> history = List.of(
            entry(5, Set.of("SECTOR_RS_RISING"), sectorRs(5, 90.0, 1, true), null, null),
            entry(6, Set.of("OUTPERFORMING_NIFTY_20D"), null, vsNifty(6, 75.0, 1, true), null) // genuinely fresh, same-or-later evidence
        );

        var result = engine.evaluateSectorTailwindSequence(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(SectorSequencePhase.COMPLETE);
    }

    @Test
    void persistedDatesReflectRealEvidenceDatesNotTheStageTwoRowDate() {
        // The Stage 2 row driving OUTPERFORMING_NIFTY_20D is dated D0+6 (row index 6), but the
        // underlying VS_NIFTY evidence backing it is genuinely dated D0+6 too here (no lag) -
        // confirm firstStepDate/lastStepDate trace to the evidence's own asOfDate.
        List<SectorInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("SECTOR_RS_RISING"), sectorRs(0, 90.0, 1, true), null, null),
            entry(6, Set.of("OUTPERFORMING_NIFTY_20D"), null, vsNifty(6, 75.0, 1, true), null)
        );

        var result = engine.evaluateSectorTailwindSequence(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().firstStepDate()).isEqualTo(D0);
        assertThat(result.get().lastStepDate()).isEqualTo(D0.plusDays(6));
    }

    // ---- IDIOSYNCRATIC_LEADERSHIP ----

    @Test
    void idiosyncraticWithoutSectorStrengtheningCompletesWhenBothFreshAndContrasting() {
        SectorEvidenceObservation vsSector = vsSector(0, 60.0, 1, true); // positive value by construction
        SectorEvidenceObservation sectorRsNotRising = sectorRsWithNegativeChange(0);
        List<SectorInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("OUTPERFORMING_SECTOR_20D"), sectorRsNotRising, null, vsSector)
        );

        var result = engine.evaluateIdiosyncraticLeadership(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(SectorSequencePhase.COMPLETE);
        assertThat(result.get().currentStep()).isEqualTo(1);
        assertThat(result.get().firstStepDate()).isEqualTo(result.get().lastStepDate());
    }

    @Test
    void bothReasonsPresentIsNotIdiosyncratic() {
        List<SectorInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("SECTOR_RS_RISING", "OUTPERFORMING_SECTOR_20D"), sectorRs(0, 90.0, 1, true), null, vsSector(0, 60.0, 1, true))
        );

        var result = engine.evaluateIdiosyncraticLeadership(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isEmpty();
    }

    @Test
    void neitherReasonPresentIsNotIdiosyncratic() {
        List<SectorInflectionHistoryEntry> history = List.of(
            entry(0, Set.of(), sectorRs(0, 90.0, 1, true), null, vsSector(0, -5.0, 0, true))
        );

        var result = engine.evaluateIdiosyncraticLeadership(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isEmpty();
    }

    /** Decision 3: missing sector evidence must never be inferred as "not rising" - the contrast can't be established at all. */
    @Test
    void missingSectorEvidenceNeverInfersNotRising() {
        List<SectorInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("OUTPERFORMING_SECTOR_20D"), null, null, vsSector(0, 60.0, 1, true))
        );

        var result = engine.evaluateIdiosyncraticLeadership(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isEmpty();
    }

    /** Decision 3: real values alone would qualify, but the two observations are too far apart in time to trust the contrast. */
    @Test
    void staleSectorEvidenceRelativeToVsSectorRejectsTheContrast() {
        SectorEvidenceObservation vsSectorFresh = vsSector(10, 60.0, 1, true); // dated D0+10
        SectorEvidenceObservation sectorRsStale = sectorRsWithNegativeChange(0); // dated D0, 10 days earlier - exceeds default lag of 5
        List<SectorInflectionHistoryEntry> history = List.of(
            entry(10, Set.of("OUTPERFORMING_SECTOR_20D"), sectorRsStale, null, vsSectorFresh)
        );

        var result = engine.evaluateIdiosyncraticLeadership(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isEmpty();
    }

    // ---- readiness: independent of whether any sequence fired ----

    @Test
    void readinessIsInsufficientHistoryBelowFiveSessions() {
        List<SectorInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("SECTOR_RS_RISING"), sectorRs(0, 90.0, 1, true), null, null),
            entry(1, Set.of(), null, null, null)
        );

        var readiness = engine.evaluateReadiness(INSTRUMENT_ID, SYMBOL, D0.plusDays(1), history);

        assertThat(readiness.readiness()).isEqualTo(SectorSequenceReadiness.INSUFFICIENT_HISTORY);
        assertThat(readiness.historySessions()).isEqualTo(2);
    }

    @Test
    void readinessIsReadyAtFiveOrMoreSessionsEvenWhenNoSequenceFires() {
        List<SectorInflectionHistoryEntry> history = List.of(
            entry(0, Set.of(), null, null, null), entry(1, Set.of(), null, null, null), entry(2, Set.of(), null, null, null),
            entry(3, Set.of(), null, null, null), entry(4, Set.of(), null, null, null)
        );

        var readiness = engine.evaluateReadiness(INSTRUMENT_ID, SYMBOL, D0.plusDays(4), history);
        var sequence = engine.evaluateSectorTailwindSequence(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(readiness.readiness()).isEqualTo(SectorSequenceReadiness.READY);
        assertThat(sequence).isEmpty();
    }

    // ---- idempotency ----

    @Test
    void sameHistoryEvaluatedTwiceProducesIdenticalResults() {
        List<SectorInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("SECTOR_RS_RISING"), sectorRs(0, 90.0, 1, true), null, null)
        );

        var first = engine.evaluateSectorTailwindSequence(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);
        var second = engine.evaluateSectorTailwindSequence(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(first).isEqualTo(second);
    }

    // ---- as-of merge defensive handling ----

    @Test
    void missingEvidenceForAReasonDegradesGracefullyNotAnNpe() {
        // hasReason is true (from the reason set) but the corresponding raw observation is null -
        // simulates an as-of merge miss; must not throw.
        List<SectorInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("SECTOR_RS_RISING"), null, null, null)
        );

        var result = engine.evaluateSectorTailwindSequence(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        // 0 confidence minus the thinness penalty (no prior) clamps to 0.0, not a negative value
        assertThat(result.get().confidence()).isCloseTo(0.0, offset(0.01));
    }

    // ---- helpers ----

    /** The raw observations may deliberately NOT correspond to this row's own dayOffset (Decision 1 staleness tests). */
    private static SectorInflectionHistoryEntry entry(
        int dayOffset, Set<String> reasons, SectorEvidenceObservation sectorRs, SectorEvidenceObservation vsNifty, SectorEvidenceObservation vsSector
    ) {
        return new SectorInflectionHistoryEntry(D0.plusDays(dayOffset), SYMBOL, new HashSet<>(reasons), sectorRs, vsNifty, vsSector);
    }

    private static SectorEvidenceObservation sectorRs(int dayOffset, double confidence, int persistenceDays, boolean hasPrior) {
        return observation(SectorMetric.SECTOR_RELATIVE_STRENGTH, dayOffset, BigDecimal.ONE, confidence, persistenceDays, hasPrior);
    }

    private static SectorEvidenceObservation sectorRsWithNegativeChange(int dayOffset) {
        return new SectorEvidenceObservation(
            SectorMetric.SECTOR_RELATIVE_STRENGTH, INSTRUMENT_ID, SYMBOL, D0.plusDays(dayOffset), D0.plusDays(dayOffset - 1),
            BigDecimal.ONE, BigDecimal.TEN, BigDecimal.valueOf(-1), 1, 90.0
        );
    }

    private static SectorEvidenceObservation vsNifty(int dayOffset, double confidence, int persistenceDays, boolean hasPrior) {
        return observation(SectorMetric.INSTRUMENT_RELATIVE_STRENGTH_VS_NIFTY, dayOffset, BigDecimal.ONE, confidence, persistenceDays, hasPrior);
    }

    private static SectorEvidenceObservation vsSector(int dayOffset, double confidence, int persistenceDays, boolean hasPrior) {
        return observation(SectorMetric.INSTRUMENT_RELATIVE_STRENGTH_VS_SECTOR, dayOffset, BigDecimal.ONE, confidence, persistenceDays, hasPrior);
    }

    private static SectorEvidenceObservation observation(SectorMetric metric, int dayOffset, BigDecimal value, double confidence, int persistenceDays, boolean hasPrior) {
        LocalDate date = D0.plusDays(dayOffset);
        return new SectorEvidenceObservation(
            metric, INSTRUMENT_ID, SYMBOL, date, hasPrior ? date.minusDays(1) : null,
            value, hasPrior ? BigDecimal.ZERO : null, BigDecimal.ONE, persistenceDays, confidence
        );
    }
}
