package com.alphagraph.sector.engine;

import com.alphagraph.common.engine.Engine;
import com.alphagraph.common.rules.ArithmeticRuleEvaluator;
import com.alphagraph.common.rules.EvaluationResult;
import com.alphagraph.common.rules.MetricContext;
import com.alphagraph.common.rules.Rule;
import com.alphagraph.common.rules.RuleEvaluator;
import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.sector.api.Leadership;
import com.alphagraph.sector.api.MoneyFlow;
import com.alphagraph.sector.api.Rotation;
import com.alphagraph.sector.api.SectorBar;
import com.alphagraph.sector.api.SectorConstituentInput;
import com.alphagraph.sector.api.SectorEngineInput;
import com.alphagraph.sector.api.SectorMomentum;
import com.alphagraph.sector.api.SectorScore;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Answers "where is money moving?" by comparing SECTORS, not individual stocks. Pure with
 * respect to already-loaded data (docs/002_Engine_Architecture.md §5): {@code intelligence}
 * assembles {@link SectorEngineInput} from market's published price/volume history and
 * {@code sector.mapping}'s sector definitions, then calls {@link #calculate}; this class never
 * fetches anything itself.
 *
 * Unlike Technical/Fundamental/Institutional (Modules 1.5-1.7), this engine's input is many
 * instruments, not one - {@code sectorConstituents} for the sector's own metrics,
 * {@code allTrackedInstruments} as the market-wide baseline Relative Strength compares against.
 *
 * Leadership (short-term, 5-day performance trend) and Rotation (medium-term, 10-day performance
 * trend) are deliberately measured over different windows so they carry distinct meaning rather
 * than duplicating each other - both are genuine trend reads (comparing a recent window to the
 * immediately preceding one), not just a snapshot classification.
 */
@Component
public class SectorEngine implements Engine<SectorEngineInput, SectorScore> {

    private static final int SHORT_WINDOW = 5;
    private static final int LONG_WINDOW = 10;
    private static final int VOLUME_LOOKBACK = 20;
    private static final int TOTAL_TRACKED_METRICS = 5;
    private static final double ROTATION_NOISE_BAND_PP = 1.0;
    private static final double LEADERSHIP_NOISE_BAND_PP = 0.5;

    private final RuleEvaluator ruleEvaluator = new ArithmeticRuleEvaluator();

    @Override
    public SectorScore calculate(SectorEngineInput input, RuleSet rules) {
        List<SectorConstituentInput> constituents = input.sectorConstituents();
        if (constituents.isEmpty()) {
            throw new IllegalArgumentException("Cannot compute a sector score with zero constituents: " + input.sectorName());
        }

        List<Double> perf10d = new ArrayList<>();
        List<Double> perf10dPrior = new ArrayList<>();
        List<Double> perf5d = new ArrayList<>();
        List<Double> perf5dPrior = new ArrayList<>();
        List<Double> relativeVolumes = new ArrayList<>();
        int advancing = 0;
        int participating = 0;

        for (SectorConstituentInput constituent : constituents) {
            List<SectorBar> bars = constituent.barsAscending();
            Double p10 = nDayReturn(bars, LONG_WINDOW, 0);
            Double p10Prior = nDayReturn(bars, LONG_WINDOW, LONG_WINDOW);
            Double p5 = nDayReturn(bars, SHORT_WINDOW, 0);
            Double p5Prior = nDayReturn(bars, SHORT_WINDOW, SHORT_WINDOW);
            Double relVol = relativeVolume(bars, VOLUME_LOOKBACK);

            if (p10 != null) {
                perf10d.add(p10);
                if (p10 > 0) {
                    advancing++;
                }
                if (relVol != null && relVol > 1.0 && p10 > 0) {
                    participating++;
                }
            }
            if (p10Prior != null) {
                perf10dPrior.add(p10Prior);
            }
            if (p5 != null) {
                perf5d.add(p5);
            }
            if (p5Prior != null) {
                perf5dPrior.add(p5Prior);
            }
            if (relVol != null) {
                relativeVolumes.add(relVol);
            }
        }

        Double sectorPerformancePct = average(perf10d);
        Double sectorPerformancePriorPct = average(perf10dPrior);
        Double sectorPerformance5d = average(perf5d);
        Double sectorPerformance5dPrior = average(perf5dPrior);
        Double sectorVolumeRatio = average(relativeVolumes);
        Double breadthPct = perf10d.isEmpty() ? null : 100.0 * advancing / perf10d.size();
        Double participationPct = perf10d.isEmpty() ? null : 100.0 * participating / perf10d.size();

        List<Double> marketPerf10d = new ArrayList<>();
        for (SectorConstituentInput constituent : input.allTrackedInstruments()) {
            Double p10 = nDayReturn(constituent.barsAscending(), LONG_WINDOW, 0);
            if (p10 != null) {
                marketPerf10d.add(p10);
            }
        }
        Double marketPerformancePct = average(marketPerf10d);

        Double relativeStrength = (sectorPerformancePct != null && marketPerformancePct != null)
            ? sectorPerformancePct - marketPerformancePct
            : null;

        double sectorScoreValue = scoreFromRules(rules, relativeStrength, breadthPct, participationPct, sectorVolumeRatio, sectorPerformancePct);

        Leadership leadership = classifyLeadership(sectorPerformance5d, sectorPerformance5dPrior);
        Rotation rotation = classifyRotation(sectorPerformancePct, sectorPerformancePriorPct);
        SectorMomentum momentum = classifyMomentum(sectorScoreValue);
        MoneyFlow moneyFlow = classifyMoneyFlow(breadthPct, participationPct, sectorVolumeRatio);

        double confidence = confidenceFrom(
            constituents.size(), relativeStrength, breadthPct, participationPct, sectorVolumeRatio, sectorPerformancePct
        );

        LocalDate asOfDate = latestDate(constituents);

        return new SectorScore(
            input.sectorId(), input.sectorName(), asOfDate,
            leadership, momentum, rotation, moneyFlow, sectorScoreValue, confidence,
            relativeStrength, sectorPerformancePct, sectorVolumeRatio, breadthPct, participationPct,
            constituents.size(), rules.version(), Instant.now()
        );
    }

    /** Return over the {@code n}-day window ending {@code offsetDays} before the most recent bar. */
    private static Double nDayReturn(List<SectorBar> bars, int n, int offsetDays) {
        int lastIndex = bars.size() - 1 - offsetDays;
        int startIndex = lastIndex - n;
        if (startIndex < 0 || lastIndex < 0) {
            return null;
        }
        double startClose = bars.get(startIndex).close().doubleValue();
        double endClose = bars.get(lastIndex).close().doubleValue();
        if (startClose == 0) {
            return null;
        }
        return (endClose - startClose) / startClose * 100.0;
    }

    private static Double relativeVolume(List<SectorBar> bars, int lookback) {
        int n = bars.size();
        if (n < lookback + 1) {
            return null;
        }
        long sum = 0;
        for (int i = n - 1 - lookback; i < n - 1; i++) {
            sum += bars.get(i).volume();
        }
        double avg = (double) sum / lookback;
        if (avg == 0) {
            return null;
        }
        return bars.get(n - 1).volume() / avg;
    }

    private static Double average(List<Double> values) {
        if (values.isEmpty()) {
            return null;
        }
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.size();
    }

    private static LocalDate latestDate(List<SectorConstituentInput> constituents) {
        return constituents.stream()
            .flatMap(c -> c.barsAscending().stream())
            .map(SectorBar::tradeDate)
            .max(LocalDate::compareTo)
            .orElse(LocalDate.now());
    }

    private double scoreFromRules(
        RuleSet rules, Double relativeStrength, Double breadthPct, Double participationPct,
        Double sectorVolumeRatio, Double sectorPerformancePct
    ) {
        Map<String, Double> metrics = new HashMap<>();
        putIfPresent(metrics, "relativeStrength", relativeStrength);
        putIfPresent(metrics, "breadthPct", breadthPct);
        putIfPresent(metrics, "participationPct", participationPct);
        putIfPresent(metrics, "sectorVolumeRatio", sectorVolumeRatio);
        putIfPresent(metrics, "sectorPerformancePct", sectorPerformancePct);

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

    private static Leadership classifyLeadership(Double recent5d, Double prior5d) {
        if (recent5d == null || prior5d == null) {
            return Leadership.STABLE;
        }
        double delta = recent5d - prior5d;
        if (delta > LEADERSHIP_NOISE_BAND_PP) {
            return Leadership.INCREASING;
        }
        if (delta < -LEADERSHIP_NOISE_BAND_PP) {
            return Leadership.DECREASING;
        }
        return Leadership.STABLE;
    }

    private static Rotation classifyRotation(Double recent10d, Double prior10d) {
        if (recent10d == null || prior10d == null) {
            return Rotation.NEUTRAL;
        }
        double delta = recent10d - prior10d;
        if (delta > ROTATION_NOISE_BAND_PP) {
            return Rotation.POSITIVE;
        }
        if (delta < -ROTATION_NOISE_BAND_PP) {
            return Rotation.NEGATIVE;
        }
        return Rotation.NEUTRAL;
    }

    private static SectorMomentum classifyMomentum(double score) {
        if (score >= 80) {
            return SectorMomentum.VERY_STRONG;
        }
        if (score >= 60) {
            return SectorMomentum.STRONG;
        }
        if (score > 40) {
            return SectorMomentum.MODERATE;
        }
        if (score > 20) {
            return SectorMomentum.WEAK;
        }
        return SectorMomentum.VERY_WEAK;
    }

    private static MoneyFlow classifyMoneyFlow(Double breadthPct, Double participationPct, Double volumeRatio) {
        boolean strong = breadthPct != null && breadthPct > 60
            && participationPct != null && participationPct > 50
            && volumeRatio != null && volumeRatio > 1.2;
        boolean weak = (breadthPct != null && breadthPct < 40) || (volumeRatio != null && volumeRatio < 0.8);

        if (strong) {
            return MoneyFlow.STRONG;
        }
        if (weak) {
            return MoneyFlow.WEAK;
        }
        return MoneyFlow.MODERATE;
    }

    /** Confidence factors in constituentCount: a 1-constituent sector's breadth/participation are trivially degenerate, not a real cross-sectional read. */
    private static double confidenceFrom(int constituentCount, Double... metrics) {
        long available = java.util.Arrays.stream(metrics).filter(m -> m != null).count();
        double base = 40.0 + 60.0 * (available / (double) TOTAL_TRACKED_METRICS);
        double sizeFactor = constituentCount >= 3 ? 1.0 : (constituentCount == 2 ? 0.75 : 0.5);
        return base * sizeFactor;
    }
}
