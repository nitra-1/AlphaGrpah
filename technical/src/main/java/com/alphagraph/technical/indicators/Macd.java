package com.alphagraph.technical.indicators;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Standard MACD(fast, slow, signal): fast EMA minus slow EMA, then an EMA of that line as the signal. */
public final class Macd {

    private Macd() {
    }

    public record MacdResult(double line, double signal, double histogram) {
    }

    public static Optional<MacdResult> of(List<Double> closesAscending, int fastPeriod, int slowPeriod, int signalPeriod) {
        List<Double> fastEma = Ema.series(closesAscending, fastPeriod);
        List<Double> slowEma = Ema.series(closesAscending, slowPeriod);

        List<Double> macdLineValid = new ArrayList<>();
        for (int i = 0; i < closesAscending.size(); i++) {
            if (!Double.isNaN(fastEma.get(i)) && !Double.isNaN(slowEma.get(i))) {
                macdLineValid.add(fastEma.get(i) - slowEma.get(i));
            }
        }

        if (macdLineValid.size() < signalPeriod) {
            return Optional.empty();
        }

        List<Double> signalSeries = Ema.series(macdLineValid, signalPeriod);
        double line = macdLineValid.get(macdLineValid.size() - 1);
        double signal = signalSeries.get(signalSeries.size() - 1);
        return Optional.of(new MacdResult(line, signal, line - signal));
    }
}
