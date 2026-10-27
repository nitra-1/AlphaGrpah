package com.alphagraph.corporate.knowledge;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AnnouncementTextBudgetTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    // 2026-10-23 20:00 UTC is already 2026-10-24 in India - the day the allowance must be keyed on
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-23T20:00:00Z"), ZoneOffset.UTC);

    @SuppressWarnings("unchecked")
    private void stubUpsert(long chars, long limit, List<Long> rows) {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(java.sql.Date.valueOf("2026-10-24")),
            eq("EXCHANGE_ANNOUNCEMENT"), eq(chars), eq(limit))).thenReturn(rows);
    }

    @Test
    void aLimitOfZeroMeansNoCapAndNeverTouchesTheDatabase() {
        AnnouncementTextBudget budget = new AnnouncementTextBudget(jdbcTemplate, 0, clock);

        assertThat(budget.tryCharge("EXCHANGE_ANNOUNCEMENT", 5_000_000)).isTrue();

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void aDocumentIsAllowedWhenTheUpsertReturnsTheNewRunningTotal() {
        stubUpsert(40_000, 1_500_000, List.of(40_000L));

        assertThat(new AnnouncementTextBudget(jdbcTemplate, 1_500_000, clock).tryCharge("EXCHANGE_ANNOUNCEMENT", 40_000)).isTrue();
    }

    @Test
    void aDocumentIsRefusedOnceItWouldPushTheDayPastTheLimit() {
        stubUpsert(700_000, 1_500_000, List.of()); // the upsert's WHERE clause matched nothing

        assertThat(new AnnouncementTextBudget(jdbcTemplate, 1_500_000, clock).tryCharge("EXCHANGE_ANNOUNCEMENT", 700_000)).isFalse();
    }

    @Test
    void theDayIsTheIndiaCalendarDayNotUtc() {
        // stubUpsert only matches Date.valueOf("2026-10-24"); a UTC-keyed implementation would have
        // asked for 2026-10-23, got the mock's default empty result, and refused this.
        stubUpsert(1_000, 500_000, List.of(1_000L));

        assertThat(new AnnouncementTextBudget(jdbcTemplate, 500_000, clock).tryCharge("EXCHANGE_ANNOUNCEMENT", 1_000)).isTrue();
    }
}
