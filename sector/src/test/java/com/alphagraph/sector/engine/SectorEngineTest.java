package com.alphagraph.sector.engine;

import com.alphagraph.common.rules.Rule;
import com.alphagraph.common.rules.RuleCondition;
import com.alphagraph.common.rules.RuleOperator;
import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.sector.api.Leadership;
import com.alphagraph.sector.api.MoneyFlow;
import com.alphagraph.sector.api.Rotation;
import com.alphagraph.sector.api.SectorBar;
import com.alphagraph.sector.api.SectorConstituentInput;
import com.alphagraph.sector.api.SectorEngineInput;
import com.alphagraph.sector.api.SectorMomentum;
import com.alphagraph.sector.api.SectorScore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SectorEngineTest {

    private final SectorEngine engine = new SectorEngine();

    // Mirrors the 5 rules seeded by common's V6 migration (Module 1.8).
    private static RuleSet defaultRuleSet() {
        List<Rule> rules = List.of(
            new Rule("sector-relative-strength", "relativeStrength", 1, List.of(
                new RuleCondition(RuleOperator.GT, 2, 1.0),
                new RuleCondition(RuleOperator.BETWEEN, 0, 2.0, 0.5),
                new RuleCondition(RuleOperator.LT, -2, -1.0))),
            new Rule("sector-breadth", "breadthPct", 1, List.of(
                new RuleCondition(RuleOperator.GT, 70, 1.0),
                new RuleCondition(RuleOperator.BETWEEN, 50, 70.0, 0.5),
                new RuleCondition(RuleOperator.LT, 30, -1.0))),
            new Rule("sector-participation", "participationPct", 1,
                List.of(new RuleCondition(RuleOperator.GT, 50, 1.0))),
            new Rule("sector-volume-expansion", "sectorVolumeRatio", 1,
                List.of(new RuleCondition(RuleOperator.GT, 1.3, 1.0))),
            new Rule("sector-performance", "sectorPerformancePct", 1, List.of(
                new RuleCondition(RuleOperator.GT, 5, 1.0),
                new RuleCondition(RuleOperator.LT, -5, -1.0)))
        );
        return new RuleSet(1, rules);
    }

    /**
     * 31 daily bars where only the checkpoints the engine actually reads are precisely
     * controlled: index 10 = 95, index 20 = 100 (prior 10-day window start/end), index 25 = 105
     * (prior 5-day window end / recent 5-day window start), index 30 = 130 (most recent close).
     * This gives: prior 10d return = (100-95)/95 = 5.26%, recent 10d return = (130-100)/100 =
     * 30% (rotation delta +24.7); prior 5d return = (105-100)/100 = 5%, recent 5d return =
     * (130-105)/105 = 23.8% (leadership delta +18.8) - both clearly accelerating. Volume is flat
     * at 1000 except the most recent bar at 2000, giving relative volume exactly 2.0.
     */
    private static List<SectorBar> acceleratingBars() {
        double[] closes = new double[31];
        for (int i = 0; i < 31; i++) {
            closes[i] = 90; // filler for indices never read exactly
        }
        closes[10] = 95;
        closes[20] = 100;
        closes[25] = 105;
        closes[30] = 130;

        List<SectorBar> bars = new ArrayList<>();
        LocalDate date = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < 31; i++) {
            long volume = (i == 30) ? 2000L : 1000L;
            bars.add(new SectorBar(date, BigDecimal.valueOf(closes[i]), volume));
            date = date.plusDays(1);
        }
        return bars;
    }

    private static List<SectorBar> flatBars() {
        List<SectorBar> bars = new ArrayList<>();
        LocalDate date = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < 31; i++) {
            bars.add(new SectorBar(date, BigDecimal.valueOf(100.0), 1000L));
            date = date.plusDays(1);
        }
        return bars;
    }

    @Test
    void acceleratingSectorWithFullParticipationProducesPositiveSignalsAcrossTheBoard() {
        UUID sectorId = UUID.randomUUID();
        List<SectorConstituentInput> constituents = List.of(
            new SectorConstituentInput(UUID.randomUUID(), "A", acceleratingBars()),
            new SectorConstituentInput(UUID.randomUUID(), "B", acceleratingBars()),
            new SectorConstituentInput(UUID.randomUUID(), "C", acceleratingBars())
        );
        // Market baseline includes one flat "other sector" instrument, so the sector's 30%
        // return clearly beats the blended market average - a genuine positive relative strength.
        List<SectorConstituentInput> allTracked = new ArrayList<>(constituents);
        allTracked.add(new SectorConstituentInput(UUID.randomUUID(), "OTHER", flatBars()));

        SectorEngineInput input = new SectorEngineInput(sectorId, "Test Sector", constituents, allTracked);
        SectorScore score = engine.calculate(input, defaultRuleSet());

        assertThat(score.sectorPerformancePct()).isCloseTo(30.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(score.breadthPct()).isEqualTo(100.0);
        assertThat(score.participationPct()).isEqualTo(100.0);
        assertThat(score.sectorVolumeRatio()).isEqualTo(2.0);
        assertThat(score.relativeStrength()).isGreaterThan(0.0);
        assertThat(score.leadership()).isEqualTo(Leadership.INCREASING);
        assertThat(score.rotation()).isEqualTo(Rotation.POSITIVE);
        assertThat(score.momentum()).isEqualTo(SectorMomentum.VERY_STRONG);
        assertThat(score.moneyFlow()).isEqualTo(MoneyFlow.STRONG);
        assertThat(score.sectorScore()).isEqualTo(100.0);
        assertThat(score.constituentCount()).isEqualTo(3);
        assertThat(score.confidence()).isEqualTo(100.0);
    }

    @Test
    void flatSingleConstituentSectorProducesNeutralSignalsAndReducedConfidence() {
        UUID sectorId = UUID.randomUUID();
        List<SectorConstituentInput> constituents = List.of(
            new SectorConstituentInput(UUID.randomUUID(), "SOLO", flatBars())
        );
        List<SectorConstituentInput> allTracked = List.of(constituents.get(0));

        SectorEngineInput input = new SectorEngineInput(sectorId, "Solo Sector", constituents, allTracked);
        SectorScore score = engine.calculate(input, defaultRuleSet());

        assertThat(score.sectorPerformancePct()).isEqualTo(0.0);
        assertThat(score.leadership()).isEqualTo(Leadership.STABLE);
        assertThat(score.rotation()).isEqualTo(Rotation.NEUTRAL);
        // A perfectly flat 0% return means zero constituents are strictly "advancing", so
        // breadth is 0% and correctly triggers the LT-30 rule - not asserting an exact score
        // here since it depends on interacting rule weights, just that it isn't inflated.
        assertThat(score.sectorScore()).isLessThan(60.0);
        // Single-constituent sectors get reduced confidence - breadth/participation are
        // trivially degenerate (0% or 100%) for exactly one stock, not a real cross-sectional read.
        assertThat(score.confidence()).isLessThan(100.0);
    }

    @Test
    void emptyConstituentsThrows() {
        SectorEngineInput input = new SectorEngineInput(UUID.randomUUID(), "Empty", List.of(), List.of());

        assertThatIllegalArgumentException().isThrownBy(() -> engine.calculate(input, defaultRuleSet()));
    }
}
