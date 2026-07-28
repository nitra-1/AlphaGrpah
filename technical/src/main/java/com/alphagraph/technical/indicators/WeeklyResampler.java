package com.alphagraph.technical.indicators;

import com.alphagraph.technical.api.DailyBar;

import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;

/**
 * Resamples daily bars into one close price per ISO week (the last trading day seen in each
 * week), so weekly indicators (e.g. a 30-week SMA) can be computed from daily data. Uses
 * {@link IsoFields} rather than a {@code Locale}-dependent {@code WeekFields} so week boundaries
 * don't shift with the JVM's default locale.
 */
public final class WeeklyResampler {

    private WeeklyResampler() {
    }

    public static List<Double> weeklyCloses(List<DailyBar> dailyBarsAscending) {
        List<Double> weeklyCloses = new ArrayList<>();
        int currentYear = Integer.MIN_VALUE;
        int currentWeek = -1;
        double lastCloseInWeek = 0;

        for (DailyBar bar : dailyBarsAscending) {
            int year = bar.tradeDate().get(IsoFields.WEEK_BASED_YEAR);
            int week = bar.tradeDate().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            if (year != currentYear || week != currentWeek) {
                if (currentWeek != -1) {
                    weeklyCloses.add(lastCloseInWeek);
                }
                currentYear = year;
                currentWeek = week;
            }
            lastCloseInWeek = bar.close().doubleValue();
        }
        if (currentWeek != -1) {
            weeklyCloses.add(lastCloseInWeek);
        }

        return weeklyCloses;
    }
}
