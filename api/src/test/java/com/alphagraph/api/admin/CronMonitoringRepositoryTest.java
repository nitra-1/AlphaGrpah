package com.alphagraph.api.admin;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@link CronMonitoringRepository#computeMissedToday} - the pure logic behind the admin
 * monitoring page's Retry button. {@code now} is passed explicitly so these stay deterministic,
 * no {@code Clock} mocking needed.
 */
class CronMonitoringRepositoryTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final String CRON_1830_IST = "0 30 18 * * *";

    private static Instant ist(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, IST).toInstant();
    }

    @Test
    void notYetDueTodayIsNeverMissed() {
        Instant now = ist(2026, 8, 24, 18, 0);
        assertThat(CronMonitoringRepository.computeMissedToday(CRON_1830_IST, null, now)).isFalse();
    }

    @Test
    void neverRunAndPastDueIsMissed() {
        Instant now = ist(2026, 8, 24, 18, 45);
        assertThat(CronMonitoringRepository.computeMissedToday(CRON_1830_IST, null, now)).isTrue();
    }

    @Test
    void lastRunYesterdayAndPastDueTodayIsMissed() {
        Instant lastStartedAt = ist(2026, 8, 23, 18, 30);
        Instant now = ist(2026, 8, 24, 18, 45);
        assertThat(CronMonitoringRepository.computeMissedToday(CRON_1830_IST, lastStartedAt, now)).isTrue();
    }

    @Test
    void lastRunTodayIsNeverMissedRegardlessOfTimeOfDay() {
        Instant lastStartedAt = ist(2026, 8, 24, 18, 30);
        Instant now = ist(2026, 8, 24, 23, 0);
        assertThat(CronMonitoringRepository.computeMissedToday(CRON_1830_IST, lastStartedAt, now)).isFalse();
    }

    @Test
    void aRunCurrentlyInProgressTodayCountsAsNotMissed() {
        // A RUNNING row's started_at is still "today" - same-day-or-not is the whole check,
        // status doesn't matter, so this doesn't need a separate status parameter at all.
        Instant lastStartedAt = ist(2026, 8, 24, 18, 30);
        Instant now = ist(2026, 8, 24, 18, 31);
        assertThat(CronMonitoringRepository.computeMissedToday(CRON_1830_IST, lastStartedAt, now)).isFalse();
    }

    @Test
    void malformedCronExpressionIsNeverMissed() {
        Instant now = ist(2026, 8, 24, 23, 0);
        assertThat(CronMonitoringRepository.computeMissedToday("not a cron", null, now)).isFalse();
        assertThat(CronMonitoringRepository.computeMissedToday(null, null, now)).isFalse();
    }
}
