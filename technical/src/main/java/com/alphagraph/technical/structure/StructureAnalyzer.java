package com.alphagraph.technical.structure;

import com.alphagraph.technical.api.BreakoutStatus;
import com.alphagraph.technical.api.DailyBar;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects swing highs/lows to classify higher-high/higher-low structure, and separately
 * classifies breakout, consolidation, and base-formation state from the raw bar series.
 */
public final class StructureAnalyzer {

    private static final int SWING_WINDOW = 3;
    private static final int BREAKOUT_LOOKBACK = 20;
    private static final double BREAKOUT_VOLUME_THRESHOLD = 1.5;
    private static final double BREAKOUT_PENDING_PROXIMITY = 0.98;
    private static final int CONSOLIDATION_WINDOW = 15;
    private static final double CONSOLIDATION_RANGE_THRESHOLD_PCT = 0.08;

    private StructureAnalyzer() {
    }

    public record StructureResult(
        boolean higherHigh, boolean higherLow, BreakoutStatus breakoutStatus,
        boolean consolidation, boolean baseFormation
    ) {
    }

    public static StructureResult analyze(List<DailyBar> barsAscending, Double relativeVolume) {
        List<Integer> swingHighIndices = findSwings(barsAscending, true);
        List<Integer> swingLowIndices = findSwings(barsAscending, false);

        boolean higherHigh = isLastHigherThanPrevious(barsAscending, swingHighIndices, true);
        boolean higherLow = isLastHigherThanPrevious(barsAscending, swingLowIndices, false);

        BreakoutStatus breakoutStatus = classifyBreakout(barsAscending, relativeVolume);
        boolean consolidation = isConsolidating(barsAscending);
        boolean baseFormation = consolidation && isAfterDecline(barsAscending);

        return new StructureResult(higherHigh, higherLow, breakoutStatus, consolidation, baseFormation);
    }

    private static List<Integer> findSwings(List<DailyBar> bars, boolean high) {
        List<Integer> swings = new ArrayList<>();
        for (int i = SWING_WINDOW; i < bars.size() - SWING_WINDOW; i++) {
            double candidate = high ? bars.get(i).high().doubleValue() : bars.get(i).low().doubleValue();
            boolean isSwing = true;
            for (int j = i - SWING_WINDOW; j <= i + SWING_WINDOW; j++) {
                if (j == i) {
                    continue;
                }
                double neighbor = high ? bars.get(j).high().doubleValue() : bars.get(j).low().doubleValue();
                if (high ? neighbor > candidate : neighbor < candidate) {
                    isSwing = false;
                    break;
                }
            }
            if (isSwing) {
                swings.add(i);
            }
        }
        return swings;
    }

    private static boolean isLastHigherThanPrevious(List<DailyBar> bars, List<Integer> swingIndices, boolean useHigh) {
        if (swingIndices.size() < 2) {
            return false;
        }
        int lastIdx = swingIndices.get(swingIndices.size() - 1);
        int prevIdx = swingIndices.get(swingIndices.size() - 2);
        double lastVal = useHigh ? bars.get(lastIdx).high().doubleValue() : bars.get(lastIdx).low().doubleValue();
        double prevVal = useHigh ? bars.get(prevIdx).high().doubleValue() : bars.get(prevIdx).low().doubleValue();
        return lastVal > prevVal;
    }

    private static BreakoutStatus classifyBreakout(List<DailyBar> bars, Double relativeVolume) {
        int n = bars.size();
        if (n < BREAKOUT_LOOKBACK + 1) {
            return BreakoutStatus.NONE;
        }

        double priorHigh = 0;
        for (int i = n - 1 - BREAKOUT_LOOKBACK; i < n - 1; i++) {
            priorHigh = Math.max(priorHigh, bars.get(i).high().doubleValue());
        }
        double latestClose = bars.get(n - 1).close().doubleValue();

        boolean brokeOut = latestClose > priorHigh;
        boolean volumeConfirmed = relativeVolume != null && relativeVolume >= BREAKOUT_VOLUME_THRESHOLD;

        if (brokeOut && volumeConfirmed) {
            return BreakoutStatus.CONFIRMED;
        }
        if (brokeOut || latestClose >= priorHigh * BREAKOUT_PENDING_PROXIMITY) {
            return BreakoutStatus.PENDING;
        }
        return BreakoutStatus.NONE;
    }

    private static boolean isConsolidating(List<DailyBar> bars) {
        int n = bars.size();
        if (n < CONSOLIDATION_WINDOW) {
            return false;
        }
        List<DailyBar> window = bars.subList(n - CONSOLIDATION_WINDOW, n);
        double highest = window.stream().mapToDouble(b -> b.high().doubleValue()).max().orElseThrow();
        double lowest = window.stream().mapToDouble(b -> b.low().doubleValue()).min().orElseThrow();
        double avgClose = window.stream().mapToDouble(b -> b.close().doubleValue()).average().orElseThrow();
        return avgClose > 0 && (highest - lowest) / avgClose <= CONSOLIDATION_RANGE_THRESHOLD_PCT;
    }

    private static boolean isAfterDecline(List<DailyBar> bars) {
        int n = bars.size();
        if (n < CONSOLIDATION_WINDOW * 2) {
            return false;
        }
        List<DailyBar> consolidationWindow = bars.subList(n - CONSOLIDATION_WINDOW, n);
        List<DailyBar> priorWindow = bars.subList(n - CONSOLIDATION_WINDOW * 2, n - CONSOLIDATION_WINDOW);
        double consolidationAvg = consolidationWindow.stream().mapToDouble(b -> b.close().doubleValue()).average().orElseThrow();
        double priorAvg = priorWindow.stream().mapToDouble(b -> b.close().doubleValue()).average().orElseThrow();
        return consolidationAvg < priorAvg;
    }
}
