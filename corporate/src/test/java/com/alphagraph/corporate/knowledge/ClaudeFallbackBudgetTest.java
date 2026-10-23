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

class ClaudeFallbackBudgetTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    // 2026-10-23 20:00 UTC is already 2026-10-24 in India - the day the budget must be keyed on
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-23T20:00:00Z"), ZoneOffset.UTC);

    @SuppressWarnings("unchecked")
    private void stubUpsert(int limit, List<Integer> rows) {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(java.sql.Date.valueOf("2026-10-24")), eq("NEWS_EXTRACTION"), eq(limit)))
            .thenReturn(rows);
    }

    @Test
    void aLimitOfZeroDisablesTheFallbackWithoutTouchingTheDatabase() {
        ClaudeFallbackBudget budget = new ClaudeFallbackBudget(jdbcTemplate, 0, clock);

        assertThat(budget.tryAcquire("NEWS_EXTRACTION")).isFalse();

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void aSlotIsGrantedWhenTheUpsertReturnsTheNewCount() {
        stubUpsert(10, List.of(1));

        assertThat(new ClaudeFallbackBudget(jdbcTemplate, 10, clock).tryAcquire("NEWS_EXTRACTION")).isTrue();
    }

    @Test
    void noSlotIsGrantedOnceTheCountHasReachedTheLimit() {
        stubUpsert(10, List.of()); // the upsert's WHERE clause matched nothing - already at the limit

        assertThat(new ClaudeFallbackBudget(jdbcTemplate, 10, clock).tryAcquire("NEWS_EXTRACTION")).isFalse();
    }

    @Test
    void theDayIsTheIndiaCalendarDayNotUtc() {
        // stubUpsert only matches Date.valueOf("2026-10-24"); a UTC-keyed implementation would
        // have asked for 2026-10-23, got the mock's default empty result, and failed this.
        stubUpsert(5, List.of(3));

        assertThat(new ClaudeFallbackBudget(jdbcTemplate, 5, clock).tryAcquire("NEWS_EXTRACTION")).isTrue();
    }
}
