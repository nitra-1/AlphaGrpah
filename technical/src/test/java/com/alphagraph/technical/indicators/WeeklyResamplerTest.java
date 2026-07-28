package com.alphagraph.technical.indicators;

import com.alphagraph.technical.api.DailyBar;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyResamplerTest {

    @Test
    void keepsOnlyTheLastCloseSeenInEachIsoWeek() {
        List<DailyBar> bars = List.of(
            bar(LocalDate.of(2026, 1, 5), 10.0),  // Monday, week 2
            bar(LocalDate.of(2026, 1, 6), 11.0),  // Tuesday, week 2
            bar(LocalDate.of(2026, 1, 12), 20.0), // Monday, week 3
            bar(LocalDate.of(2026, 1, 13), 21.0)  // Tuesday, week 3
        );

        List<Double> weeklyCloses = WeeklyResampler.weeklyCloses(bars);

        assertThat(weeklyCloses).containsExactly(11.0, 21.0);
    }

    private static DailyBar bar(LocalDate date, double close) {
        BigDecimal price = BigDecimal.valueOf(close);
        return new DailyBar(date, price, price, price, price, 1000L, null);
    }
}
