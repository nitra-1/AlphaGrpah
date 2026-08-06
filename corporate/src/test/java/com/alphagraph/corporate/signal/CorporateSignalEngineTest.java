package com.alphagraph.corporate.signal;

import com.alphagraph.common.rules.Rule;
import com.alphagraph.common.rules.RuleCondition;
import com.alphagraph.common.rules.RuleOperator;
import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.corporate.api.CorporateRating;
import com.alphagraph.corporate.api.CorporateScore;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Mirrors the seeded corporate-* rules (common/V11) exactly, so thresholds tested here match production. */
class CorporateSignalEngineTest {

    private final CorporateSignalEngine engine = new CorporateSignalEngine();
    private final UUID instrumentId = UUID.randomUUID();
    private final LocalDate asOfDate = LocalDate.of(2026, 6, 1);

    private static final RuleSet RULES = new RuleSet(1, List.of(
        new Rule("corporate-orderbook-strength", "corporateOrderBookScore", 1, List.of(
            new RuleCondition(RuleOperator.GTE, 70, null, 0.75),
            new RuleCondition(RuleOperator.LTE, 30, null, -0.75)
        )),
        new Rule("corporate-management-strength", "corporateManagementScore", 1, List.of(
            new RuleCondition(RuleOperator.GTE, 70, null, 0.75),
            new RuleCondition(RuleOperator.LTE, 30, null, -0.75)
        )),
        new Rule("corporate-news-catalyst-strength", "corporateNewsCatalystScore", 1, List.of(
            new RuleCondition(RuleOperator.GTE, 70, null, 0.5),
            new RuleCondition(RuleOperator.LTE, 30, null, -0.5)
        )),
        new Rule("corporate-event-signal", "corporateEventNetSignal", 1, List.of(
            new RuleCondition(RuleOperator.GTE, 2, null, 1.0),
            new RuleCondition(RuleOperator.LTE, -1, null, -1.0)
        ))
    ));

    @Test
    void allFourDomainsStrongYieldsExcellentRatingAndFullConfidence() {
        CorporateSignalInput input = new CorporateSignalInput(
            instrumentId, "TEST", 85.0, 80.0, 75.0, 3, 3, asOfDate
        );

        CorporateScore score = engine.calculate(input, RULES);

        // raw = 0.75 + 0.75 + 0.5 + 1.0 = 3.0 -> 50 + 30 = 80
        assertThat(score.corporateScore()).isEqualTo(80.0);
        assertThat(score.corporateRating()).isEqualTo(CorporateRating.EXCELLENT);
        assertThat(score.confidence()).isEqualTo(100.0);
        assertThat(score.orderBookScore()).isEqualTo(85.0);
        assertThat(score.managementScore()).isEqualTo(80.0);
        assertThat(score.newsCatalystScore()).isEqualTo(75.0);
        assertThat(score.eventNetSignal()).isEqualTo(3);
    }

    @Test
    void allFourDomainsWeakYieldsPoorRating() {
        CorporateSignalInput input = new CorporateSignalInput(
            instrumentId, "TEST", 20.0, 15.0, 10.0, -2, 2, asOfDate
        );

        CorporateScore score = engine.calculate(input, RULES);

        // raw = -0.75 - 0.75 - 0.5 - 1.0 = -3.0 -> 50 - 30 = 20
        assertThat(score.corporateScore()).isEqualTo(20.0);
        assertThat(score.corporateRating()).isEqualTo(CorporateRating.POOR);
    }

    @Test
    void onlyOrderBookPresentYieldsLowestNonZeroConfidence() {
        CorporateSignalInput input = new CorporateSignalInput(
            instrumentId, "TEST", 85.0, null, null, 0, 0, asOfDate
        );

        CorporateScore score = engine.calculate(input, RULES);

        assertThat(score.confidence()).isEqualTo(55.0);
        assertThat(score.managementScore()).isNull();
        assertThat(score.newsCatalystScore()).isNull();
    }

    @Test
    void missingDomainsContributeNothingRatherThanBeingPenalized() {
        CorporateSignalInput input = new CorporateSignalInput(
            instrumentId, "TEST", null, null, null, 0, 0, asOfDate
        );

        CorporateScore score = engine.calculate(input, RULES);

        assertThat(score.corporateScore()).isEqualTo(50.0);
        assertThat(score.corporateRating()).isEqualTo(CorporateRating.NEUTRAL);
        assertThat(score.confidence()).isEqualTo(40.0);
    }

    @Test
    void eventCountAloneCanDriveTheScoreWithoutAnySnapshotDomain() {
        CorporateSignalInput input = new CorporateSignalInput(
            instrumentId, "TEST", null, null, null, 3, 3, asOfDate
        );

        CorporateScore score = engine.calculate(input, RULES);

        // raw = 1.0 -> 50 + 10 = 60
        assertThat(score.corporateScore()).isEqualTo(60.0);
        assertThat(score.confidence()).isEqualTo(55.0);
    }

    @Test
    void ratingBandBoundariesAreInclusiveAtTheLowerEdge() {
        CorporateSignalInput neutralFloor = new CorporateSignalInput(instrumentId, "TEST", null, null, null, 0, 0, asOfDate);
        assertThat(engine.calculate(neutralFloor, new RuleSet(1, List.of())).corporateRating()).isEqualTo(CorporateRating.NEUTRAL);
    }

    @Test
    void scoreIsClampedBetweenZeroAndOneHundred() {
        RuleSet extremeRules = new RuleSet(1, List.of(
            new Rule("corporate-event-signal", "corporateEventNetSignal", 1, List.of(
                new RuleCondition(RuleOperator.GTE, 1, null, 100.0)
            ))
        ));
        CorporateSignalInput input = new CorporateSignalInput(instrumentId, "TEST", null, null, null, 5, 5, asOfDate);

        CorporateScore score = engine.calculate(input, extremeRules);

        assertThat(score.corporateScore()).isEqualTo(100.0);
    }
}
