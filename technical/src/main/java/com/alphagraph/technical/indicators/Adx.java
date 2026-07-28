package com.alphagraph.technical.indicators;

import java.util.List;
import java.util.OptionalDouble;

/**
 * Wilder's ADX: true range and directional movement (+DM/-DM) are each Wilder-smoothed into
 * +DI/-DI, combined into DX, and DX is itself Wilder-smoothed into ADX. Needs roughly
 * {@code 2 * period} bars before a value is available.
 */
public final class Adx {

    private Adx() {
    }

    public static OptionalDouble of(List<Double> highs, List<Double> lows, List<Double> closes, int period) {
        int n = closes.size();
        if (n < period * 2) {
            return OptionalDouble.empty();
        }

        double[] tr = new double[n];
        double[] plusDm = new double[n];
        double[] minusDm = new double[n];
        for (int i = 1; i < n; i++) {
            double hl = highs.get(i) - lows.get(i);
            double hc = Math.abs(highs.get(i) - closes.get(i - 1));
            double lc = Math.abs(lows.get(i) - closes.get(i - 1));
            tr[i] = Math.max(hl, Math.max(hc, lc));

            double upMove = highs.get(i) - highs.get(i - 1);
            double downMove = lows.get(i - 1) - lows.get(i);
            plusDm[i] = (upMove > downMove && upMove > 0) ? upMove : 0;
            minusDm[i] = (downMove > upMove && downMove > 0) ? downMove : 0;
        }

        double smoothTr = 0;
        double smoothPlusDm = 0;
        double smoothMinusDm = 0;
        for (int i = 1; i <= period; i++) {
            smoothTr += tr[i];
            smoothPlusDm += plusDm[i];
            smoothMinusDm += minusDm[i];
        }

        double[] dx = new double[n];
        double plusDi = 100 * smoothPlusDm / smoothTr;
        double minusDi = 100 * smoothMinusDm / smoothTr;
        dx[period] = (plusDi + minusDi) == 0 ? 0 : 100 * Math.abs(plusDi - minusDi) / (plusDi + minusDi);

        for (int i = period + 1; i < n; i++) {
            smoothTr = smoothTr - (smoothTr / period) + tr[i];
            smoothPlusDm = smoothPlusDm - (smoothPlusDm / period) + plusDm[i];
            smoothMinusDm = smoothMinusDm - (smoothMinusDm / period) + minusDm[i];

            plusDi = 100 * smoothPlusDm / smoothTr;
            minusDi = 100 * smoothMinusDm / smoothTr;
            dx[i] = (plusDi + minusDi) == 0 ? 0 : 100 * Math.abs(plusDi - minusDi) / (plusDi + minusDi);
        }

        int firstDxIndex = period;
        int lastDxIndex = n - 1;
        if (lastDxIndex - firstDxIndex + 1 < period) {
            return OptionalDouble.empty();
        }

        double adx = 0;
        for (int i = firstDxIndex; i < firstDxIndex + period; i++) {
            adx += dx[i];
        }
        adx /= period;

        for (int i = firstDxIndex + period; i <= lastDxIndex; i++) {
            adx = (adx * (period - 1) + dx[i]) / period;
        }

        return OptionalDouble.of(adx);
    }
}
