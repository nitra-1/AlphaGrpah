package com.alphagraph.ownership.transformation;

import com.alphagraph.common.rules.Rule;
import com.alphagraph.common.rules.RuleCondition;
import com.alphagraph.common.rules.RuleOperator;
import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.ownership.api.BulkDeal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OwnershipTransformationEngineTest {

    private static final UUID INSTRUMENT_ID = UUID.randomUUID();
    private static final String SYMBOL = "RELIANCE";
    private static final LocalDate PRIOR_PERIOD_END = LocalDate.of(2026, 3, 31);
    private static final LocalDate CURRENT_PERIOD_END = LocalDate.of(2026, 6, 30);

    private final OwnershipTransformationEngine engine = new OwnershipTransformationEngine();

    @Test
    void cleanInstitutionalExpansionFiresWhenBothFiiAndDiiRise() {
        // promoter flat (0 change), FII +0.60pp, DII +0.60pp - both cross the signal threshold (a
        // ladder score of 45 at absChangePp=0.60, well above the 30 threshold).
        var periods = List.of(
            period(PRIOR_PERIOD_END, "50.00", "16.00", "18.00"),
            period(CURRENT_PERIOD_END, "50.00", "16.60", "18.60")
        );

        var calculation = engine.calculate(periods, List.of(), ruleSet()).orElseThrow();

        assertThat(calculation.result().primaryState()).isEqualTo(TransformationState.INSTITUTIONAL_OWNERSHIP_EXPANSION);
        assertThat(reasonCodes(calculation)).contains("FII_ACCUMULATION", "DII_ACCUMULATION", "INSTITUTIONAL_OWNERSHIP_EXPANSION");
        assertThat(calculation.evidence()).isNotEmpty();
    }

    @Test
    void institutionalExpansionPicksTheLargerOfTwoPositiveDrivingMetrics() {
        // Both FII and DII rise (equal signal shape to the test above), but DII rises further -
        // driving metric must be DII, the larger of the two positive changes, not FII.
        var periods = List.of(
            period(PRIOR_PERIOD_END, "50.00", "16.00", "18.00"),
            period(CURRENT_PERIOD_END, "50.00", "16.60", "19.50")
        );

        var calculation = engine.calculate(periods, List.of(), ruleSet()).orElseThrow();

        assertThat(calculation.result().primaryState()).isEqualTo(TransformationState.INSTITUTIONAL_OWNERSHIP_EXPANSION);
        assertThat(calculation.result().drivingMetric()).isEqualTo(TransformationMetric.DII);
        assertThat(calculation.result().level()).isEqualByComparingTo("19.50");
        assertThat(calculation.result().change()).isEqualByComparingTo("1.50");
        assertThat(calculation.result().velocityBand()).isEqualTo(com.alphagraph.common.inflection.VelocityBand.MODERATE);
    }

    @Test
    void contradictionDrivingMetricIsWhicheverSideActuallyParticipated() {
        // FII rises past the signal threshold, DII doesn't move at all (never signals) - even
        // though picking by raw magnitude alone could favor a non-participating metric in other
        // shapes, here the driving metric must be FII specifically because it's the only one that
        // actually participated in the contradiction.
        var periods = List.of(
            period(PRIOR_PERIOD_END, "50.00", "16.00", "18.00"),
            period(CURRENT_PERIOD_END, "49.40", "16.60", "18.00")
        );

        var calculation = engine.calculate(periods, List.of(), ruleSet()).orElseThrow();

        assertThat(calculation.result().primaryState()).isEqualTo(TransformationState.OWNERSHIP_CONTRADICTION);
        assertThat(calculation.result().drivingMetric()).isEqualTo(TransformationMetric.FII);
    }

    @Test
    void drivingConfidenceCombinesBaseAndPersistenceBonusThenClamps() {
        // FII (base confidence 75, not PROMOTER/PUBLIC) rises 2.50pp over exactly one quarter -
        // velocity 2.50 bands STRONG. Two periods means persistence=1 (the only transition matches
        // itself) - persistenceBonus = min(10, 2*1) = 2. No thinness penalty (a real prior exists).
        // Expected: 75 + 2 - 0 = 77.
        var periods = List.of(
            period(PRIOR_PERIOD_END, "50.00", "16.00", "18.00"),
            period(CURRENT_PERIOD_END, "50.00", "18.50", "18.00")
        );

        var calculation = engine.calculate(periods, List.of(), ruleSet()).orElseThrow();

        assertThat(calculation.result().primaryState()).isEqualTo(TransformationState.FII_ACCUMULATION);
        assertThat(calculation.result().drivingMetric()).isEqualTo(TransformationMetric.FII);
        assertThat(calculation.result().velocityBand()).isEqualTo(com.alphagraph.common.inflection.VelocityBand.STRONG);
        assertThat(calculation.result().persistence()).isEqualTo(1);
        assertThat(calculation.result().confidence()).isEqualTo(77.0);
    }

    @Test
    void noClearSignalHasNoDrivingMetricOrInflectionFields() {
        var periods = List.of(
            period(PRIOR_PERIOD_END, "50.00", "16.00", "18.00"),
            period(CURRENT_PERIOD_END, "50.00", "16.00", "18.00")
        );

        var calculation = engine.calculate(periods, List.of(), ruleSet()).orElseThrow();

        assertThat(calculation.result().primaryState()).isEqualTo(TransformationState.NO_CLEAR_SIGNAL);
        assertThat(calculation.result().drivingMetric()).isNull();
        assertThat(calculation.result().level()).isNull();
        assertThat(calculation.result().change()).isNull();
        assertThat(calculation.result().velocityBand()).isNull();
        assertThat(calculation.result().persistence()).isEqualTo(0);
    }

    @Test
    void promoterDilutionFiresOnItsOwn() {
        var periods = List.of(
            period(PRIOR_PERIOD_END, "50.00", "16.00", "18.00"),
            period(CURRENT_PERIOD_END, "49.40", "16.00", "18.00")
        );

        var calculation = engine.calculate(periods, List.of(), ruleSet()).orElseThrow();

        assertThat(calculation.result().primaryState()).isEqualTo(TransformationState.PROMOTER_DILUTION);
        assertThat(reasonCodes(calculation)).containsExactly("PROMOTER_DILUTION");
    }

    @Test
    void contradictionWinsThePriorityLadderButStillRecordsTheAccumulationReason() {
        // Institutions accumulating while the promoter dilutes in the same quarter - a real
        // tension worth flagging first, but FII_ACCUMULATION must still show up as a reason so it
        // isn't hidden behind the higher-priority contradiction label.
        var periods = List.of(
            period(PRIOR_PERIOD_END, "50.00", "16.00", "18.00"),
            period(CURRENT_PERIOD_END, "49.40", "16.60", "18.00")
        );

        var calculation = engine.calculate(periods, List.of(), ruleSet()).orElseThrow();

        assertThat(calculation.result().primaryState()).isEqualTo(TransformationState.OWNERSHIP_CONTRADICTION);
        assertThat(reasonCodes(calculation)).contains("OWNERSHIP_CONTRADICTION", "FII_ACCUMULATION", "PROMOTER_DILUTION");
    }

    @Test
    void missingPriorPeriodStillEvidencesEveryMetricAtLowerConfidenceWithNoStateFiring() {
        var periods = List.of(period(CURRENT_PERIOD_END, "50.00", "16.00", "18.00"));

        var calculation = engine.calculate(periods, List.of(), ruleSet()).orElseThrow();

        assertThat(calculation.result().primaryState()).isEqualTo(TransformationState.NO_CLEAR_SIGNAL);
        assertThat(calculation.result().confidence()).isEqualTo(40.0);
        assertThat(calculation.evidence()).isNotEmpty();
        assertThat(calculation.evidence()).allSatisfy(observation -> {
            assertThat(observation.changePp()).isNull();
            assertThat(observation.priorValue()).isNull();
            assertThat(observation.confidence()).isEqualTo(40.0);
        });
    }

    @Test
    void bulkBuyingWithOwnershipExpansionFiresWhenRealNetBuyingPrecedesAnFiiIncrease() {
        var periods = List.of(
            period(PRIOR_PERIOD_END, "50.00", "16.00", "18.00"),
            period(CURRENT_PERIOD_END, "50.00", "16.60", "18.00")
        );
        List<BulkDeal> bulkDeals = List.of(
            new BulkDeal(INSTRUMENT_ID, SYMBOL, PRIOR_PERIOD_END.plusDays(10), "SOME FUND", "BUY", 100_000L, BigDecimal.valueOf(50), "BULK")
        );

        var calculation = engine.calculate(periods, bulkDeals, ruleSet()).orElseThrow();

        assertThat(calculation.result().primaryState()).isEqualTo(TransformationState.BULK_BUYING_WITH_OWNERSHIP_EXPANSION);
        assertThat(reasonCodes(calculation)).contains("BULK_BUYING_WITH_OWNERSHIP_EXPANSION", "FII_ACCUMULATION");
    }

    @Test
    void emptyPeriodsReturnsEmpty() {
        assertThat(engine.calculate(List.of(), List.of(), ruleSet())).isEmpty();
    }

    @Test
    void asOfDateUsesDataAvailableFromWhenPromoterDrivesTheResult() {
        // PROMOTER_DILUTION - a real information-availability date (the live summary JSON's own
        // collection date), never CURRENT_PERIOD_END itself and never today's calendar date.
        var periods = List.of(
            period(PRIOR_PERIOD_END, "50.00", "16.00", "18.00"),
            period(CURRENT_PERIOD_END, "49.40", "16.00", "18.00")
        );

        var calculation = engine.calculate(periods, List.of(), ruleSet()).orElseThrow();

        assertThat(calculation.result().drivingMetric()).isEqualTo(TransformationMetric.PROMOTER);
        assertThat(calculation.result().asOfDate()).isEqualTo(CURRENT_PERIOD_END.plusDays(5));
    }

    @Test
    void asOfDateUsesXbrlDataAvailableFromWhenAnXbrlGatedMetricDrivesTheResult() {
        // FII is one of the 7 metrics only ever real once XBRL enrichment runs - its own
        // availability date must be used, never the earlier promoter/public collection date.
        var periods = List.of(
            period(PRIOR_PERIOD_END, "50.00", "16.00", "18.00"),
            period(CURRENT_PERIOD_END, "50.00", "18.50", "18.00")
        );

        var calculation = engine.calculate(periods, List.of(), ruleSet()).orElseThrow();

        assertThat(calculation.result().drivingMetric()).isEqualTo(TransformationMetric.FII);
        assertThat(calculation.result().asOfDate()).isEqualTo(CURRENT_PERIOD_END.plusDays(20));
    }

    @Test
    void noClearSignalsAsOfDateFallsBackToDataAvailableFromNeverXbrl() {
        var periods = List.of(
            period(PRIOR_PERIOD_END, "50.00", "16.00", "18.00"),
            period(CURRENT_PERIOD_END, "50.00", "16.00", "18.00")
        );

        var calculation = engine.calculate(periods, List.of(), ruleSet()).orElseThrow();

        assertThat(calculation.result().primaryState()).isEqualTo(TransformationState.NO_CLEAR_SIGNAL);
        assertThat(calculation.result().asOfDate()).isEqualTo(CURRENT_PERIOD_END.plusDays(5));
    }

    private static List<String> reasonCodes(TransformationCalculation calculation) {
        return calculation.result().reasons().stream().map(ReasonCode::code).toList();
    }

    private static TransformationShareholdingPeriod period(LocalDate periodEnd, String promoter, String fii, String dii) {
        // Real, distinct dates - initial collection (dataAvailableFrom) always precedes XBRL
        // enrichment (xbrlDataAvailableFrom) by construction, same as the real pipeline.
        return new TransformationShareholdingPeriod(
            INSTRUMENT_ID, SYMBOL, periodEnd, periodEnd.plusDays(5), periodEnd.plusDays(20),
            new BigDecimal(promoter), new BigDecimal(fii), new BigDecimal(dii),
            null, null, null, null, null, null
        );
    }

    private static RuleSet ruleSet() {
        List<RuleCondition> ladder = List.of(
            new RuleCondition(RuleOperator.ALWAYS, 0, 10),
            new RuleCondition(RuleOperator.GTE, 0.25, 15),
            new RuleCondition(RuleOperator.GTE, 0.50, 20),
            new RuleCondition(RuleOperator.GTE, 1.00, 25),
            new RuleCondition(RuleOperator.GTE, 2.00, 30)
        );
        return new RuleSet(1, List.of(
            new Rule("ownership-transformation-promoter-change", "absChangePp", 1, ladder),
            new Rule("ownership-transformation-fii-change", "absChangePp", 1, ladder),
            new Rule("ownership-transformation-dii-change", "absChangePp", 1, ladder)
        ));
    }
}
