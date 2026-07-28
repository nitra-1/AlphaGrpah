package com.alphagraph.technical.engine;

import com.alphagraph.common.engine.Engine;
import com.alphagraph.common.rules.ArithmeticRuleEvaluator;
import com.alphagraph.common.rules.EvaluationResult;
import com.alphagraph.common.rules.MetricContext;
import com.alphagraph.common.rules.Rule;
import com.alphagraph.common.rules.RuleEvaluator;
import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.technical.api.BreakoutStatus;
import com.alphagraph.technical.api.DailyBar;
import com.alphagraph.technical.api.Momentum;
import com.alphagraph.technical.api.TechnicalEngineInput;
import com.alphagraph.technical.api.TechnicalScore;
import com.alphagraph.technical.api.Trend;
import com.alphagraph.technical.api.VolumeState;
import com.alphagraph.technical.indicators.Adx;
import com.alphagraph.technical.indicators.Atr;
import com.alphagraph.technical.indicators.Macd;
import com.alphagraph.technical.indicators.Obv;
import com.alphagraph.technical.indicators.RelativeVolume;
import com.alphagraph.technical.indicators.Rsi;
import com.alphagraph.technical.indicators.Sma;
import com.alphagraph.technical.indicators.WeeklyResampler;
import com.alphagraph.technical.structure.StageClassifier;
import com.alphagraph.technical.structure.StructureAnalyzer;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Answers "what is the market telling us?" from price and volume alone. Pure with respect to
 * already-loaded data (docs/002_Engine_Architecture.md §5): {@code intelligence} assembles
 * {@link TechnicalEngineInput} from market's published API and calls {@link #calculate}; this
 * class never fetches anything itself.
 *
 * The raw-score-to-0-100 {@code marketBehaviourScore} mapping ({@code 50 + rawScore * 10},
 * clamped) is an engine implementation choice, not itself a {@code Rule} — the individual
 * threshold/weight decisions that produce {@code rawScore} ARE rules (seeded in
 * common.rule_definitions, see Module 1.5's migration), so those remain editable without a
 * redeploy per docs/002_Engine_Architecture.md §4; the final normalization formula is
 * intentionally simple and documented here instead of hidden in a rule with no obvious meaning.
 */
@Component
public class TechnicalEngine implements Engine<TechnicalEngineInput, TechnicalScore> {

    private static final int SMA_SHORT = 20;
    private static final int SMA_MEDIUM = 50;
    private static final int SMA_LONG = 200;
    private static final int WEEKLY_SMA_PERIOD = 30;
    private static final int RSI_PERIOD = 14;
    private static final int MACD_FAST = 12;
    private static final int MACD_SLOW = 26;
    private static final int MACD_SIGNAL = 9;
    private static final int ADX_PERIOD = 14;
    private static final int ATR_PERIOD = 14;
    private static final int RELATIVE_VOLUME_LOOKBACK = 20;
    private static final int OBV_SLOPE_LOOKBACK = 10;
    private static final double SIDEWAYS_BAND_PCT = 2.0;

    private final RuleEvaluator ruleEvaluator = new ArithmeticRuleEvaluator();

    @Override
    public TechnicalScore calculate(TechnicalEngineInput input, RuleSet rules) {
        List<DailyBar> bars = input.dailyBars();
        if (bars.isEmpty()) {
            throw new IllegalArgumentException("Cannot compute a technical score with zero daily bars: " + input.symbol());
        }

        List<Double> closes = bars.stream().map(b -> b.close().doubleValue()).toList();
        List<Double> highs = bars.stream().map(b -> b.high().doubleValue()).toList();
        List<Double> lows = bars.stream().map(b -> b.low().doubleValue()).toList();
        List<Long> volumes = bars.stream().map(DailyBar::volume).toList();
        double lastClose = closes.get(closes.size() - 1);

        OptionalDouble sma20 = Sma.of(closes, SMA_SHORT);
        OptionalDouble sma50 = Sma.of(closes, SMA_MEDIUM);
        OptionalDouble sma200 = Sma.of(closes, SMA_LONG);
        List<Double> weeklyCloses = WeeklyResampler.weeklyCloses(bars);
        OptionalDouble weeklySma30 = Sma.of(weeklyCloses, WEEKLY_SMA_PERIOD);

        OptionalDouble rsi14 = Rsi.of(closes, RSI_PERIOD);
        Optional<Macd.MacdResult> macd = Macd.of(closes, MACD_FAST, MACD_SLOW, MACD_SIGNAL);
        OptionalDouble adx14 = Adx.of(highs, lows, closes, ADX_PERIOD);
        OptionalDouble atr14 = Atr.of(highs, lows, closes, ATR_PERIOD);
        long obv = Obv.of(closes, volumes);
        double obvSlope = Obv.slope(closes, volumes, OBV_SLOPE_LOOKBACK);
        OptionalDouble relativeVolume = RelativeVolume.of(volumes, RELATIVE_VOLUME_LOOKBACK);

        Double priceVsSma50Pct = sma50.isPresent() ? percentDiff(lastClose, sma50.getAsDouble()) : null;
        Double priceVsSma200Pct = sma200.isPresent() ? percentDiff(lastClose, sma200.getAsDouble()) : null;
        Double rsiValue = rsi14.isPresent() ? rsi14.getAsDouble() : null;
        Double adxValue = adx14.isPresent() ? adx14.getAsDouble() : null;
        Double relativeVolumeValue = relativeVolume.isPresent() ? relativeVolume.getAsDouble() : null;
        Double macdHistogram = macd.map(Macd.MacdResult::histogram).orElse(null);

        double marketBehaviourScore = scoreFromRules(rules, priceVsSma50Pct, priceVsSma200Pct, rsiValue, macdHistogram, adxValue, relativeVolumeValue, obvSlope);

        Trend trend = classifyTrend(priceVsSma50Pct, priceVsSma200Pct, rsiValue);
        double trendConfidence = trendConfidenceOf(priceVsSma50Pct == null ? 0 : priceVsSma50Pct, priceVsSma200Pct, adxValue);
        Momentum momentum = classifyMomentum(rsiValue, macdHistogram);
        VolumeState volumeState = classifyVolume(relativeVolumeValue, obvSlope);

        StructureAnalyzer.StructureResult structure = StructureAnalyzer.analyze(bars, relativeVolumeValue);
        Integer stage = StageClassifier.classify(weeklyCloses, WEEKLY_SMA_PERIOD).orElse(null);

        return new TechnicalScore(
            input.instrumentId(), input.symbol(), bars.get(bars.size() - 1).tradeDate(),
            trend, trendConfidence, momentum, volumeState, structure.breakoutStatus(), stage, marketBehaviourScore,
            sma20.isPresent() ? sma20.getAsDouble() : null,
            sma50.isPresent() ? sma50.getAsDouble() : null,
            sma200.isPresent() ? sma200.getAsDouble() : null,
            weeklySma30.isPresent() ? weeklySma30.getAsDouble() : null,
            rsiValue, macd.map(Macd.MacdResult::line).orElse(null), macd.map(Macd.MacdResult::signal).orElse(null),
            macdHistogram, adxValue, atr14.isPresent() ? atr14.getAsDouble() : null, obv, relativeVolumeValue,
            rules.version(), Instant.now()
        );
    }

    private static double percentDiff(double value, double reference) {
        return (value - reference) / reference * 100.0;
    }

    private double scoreFromRules(
        RuleSet rules, Double priceVsSma50Pct, Double priceVsSma200Pct, Double rsi14,
        Double macdHistogram, Double adx14, Double relativeVolume, double obvSlope
    ) {
        Map<String, Double> metrics = new HashMap<>();
        putIfPresent(metrics, "priceVsSma50Pct", priceVsSma50Pct);
        putIfPresent(metrics, "priceVsSma200Pct", priceVsSma200Pct);
        putIfPresent(metrics, "rsi14", rsi14);
        putIfPresent(metrics, "macdHistogram", macdHistogram);
        putIfPresent(metrics, "adx14", adx14);
        putIfPresent(metrics, "relativeVolume", relativeVolume);
        metrics.put("obvSlope", obvSlope);

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

    private static Trend classifyTrend(Double priceVsSma50Pct, Double priceVsSma200Pct, Double rsi14) {
        if (priceVsSma50Pct == null) {
            return Trend.SIDEWAYS;
        }
        boolean aboveSma50 = priceVsSma50Pct > 0;
        if (Math.abs(priceVsSma50Pct) < SIDEWAYS_BAND_PCT) {
            return Trend.SIDEWAYS;
        }

        boolean aboveSma200 = priceVsSma200Pct != null && priceVsSma200Pct > 0;
        boolean longTermConfirms = priceVsSma200Pct == null || (aboveSma50 == aboveSma200);
        boolean rsiSupportsStrength = rsi14 != null && (aboveSma50 ? rsi14 > 55 : rsi14 < 45);
        boolean strong = longTermConfirms && rsiSupportsStrength;

        if (aboveSma50) {
            return strong ? Trend.STRONG_UPTREND : Trend.UPTREND;
        }
        return strong ? Trend.STRONG_DOWNTREND : Trend.DOWNTREND;
    }

    private static double trendConfidenceOf(double priceVsSma50Pct, Double priceVsSma200Pct, Double adx14) {
        double confidence = 50.0;
        confidence += Math.min(Math.abs(priceVsSma50Pct) * 2, 30);
        if (adx14 != null && adx14 > 25) {
            confidence += 10;
        }
        if (priceVsSma200Pct != null) {
            confidence += 5;
        }
        return Math.min(confidence, 100.0);
    }

    private static Momentum classifyMomentum(Double rsi14, Double macdHistogram) {
        if (rsi14 == null || macdHistogram == null) {
            return Momentum.NEUTRAL;
        }
        if (rsi14 > 50 && macdHistogram > 0) {
            return Momentum.IMPROVING;
        }
        if (rsi14 < 50 && macdHistogram < 0) {
            return Momentum.WEAKENING;
        }
        return Momentum.NEUTRAL;
    }

    private static VolumeState classifyVolume(Double relativeVolume, double obvSlope) {
        if (relativeVolume == null) {
            return VolumeState.AVERAGE;
        }
        if (relativeVolume >= 1.5 && obvSlope > 0) {
            return VolumeState.STRONG;
        }
        if (relativeVolume < 0.7 || obvSlope < 0) {
            return VolumeState.WEAK;
        }
        return VolumeState.AVERAGE;
    }
}
