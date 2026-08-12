package com.alphagraph.scheduler.monitoring;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobRunTrackerImplTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final JobRunTrackerImpl tracker = new JobRunTrackerImpl(jdbcTemplate);

    @Test
    void recordsRunningThenSuccessWithNoSummaryWhenNoTableGiven() {
        tracker.run("test-job", () -> { });

        verify(jdbcTemplate).update(contains("INSERT INTO scheduler.job_runs"), any(), eq("test-job"), any());
        verify(jdbcTemplate).update(contains("SET status = 'SUCCESS'"), any(), isNull(), any());
    }

    @Test
    void recordsFailedWithTruncatedErrorMessageWhenWorkThrows() {
        RuntimeException boom = new RuntimeException("x".repeat(3000));

        tracker.run("test-job", () -> { throw boom; });

        verify(jdbcTemplate).update(contains("SET status = 'FAILED'"), any(), argThat(msg -> msg instanceof String s && s.length() == 2000), any());
    }

    @Test
    void handlesANullExceptionMessageWithoutThrowing() {
        tracker.run("test-job", () -> { throw new NullPointerException(); });

        verify(jdbcTemplate).update(contains("SET status = 'FAILED'"), any(), isNull(), any());
    }

    @Test
    void summarizesRowsWrittenToTheGivenTableWhenWorkSucceeds() {
        when(jdbcTemplate.queryForObject(contains("financial.fundamental_scores"), eq(Long.class), any(), any())).thenReturn(42L);

        tracker.run("fundamental-analysis", () -> { }, "financial.fundamental_scores", "computed_at");

        verify(jdbcTemplate).update(contains("SET status = 'SUCCESS'"), any(), eq("42 row(s) written to financial.fundamental_scores"), any());
    }

    @Test
    void neverQueriesForASummaryWhenWorkFails() {
        tracker.run("fundamental-analysis", () -> { throw new RuntimeException("boom"); }, "financial.fundamental_scores", "computed_at");

        verify(jdbcTemplate, never()).queryForObject(contains("financial.fundamental_scores"), eq(Long.class), any(), any());
    }

    @Test
    void recordsFailedRatherThanRunningAnUnsafeLookingTableName() {
        tracker.run("bad-job", () -> { }, "financial.fundamental_scores; DROP TABLE x", "computed_at");

        verify(jdbcTemplate, never()).queryForObject(anyString(), eq(Long.class), any(), any());
        verify(jdbcTemplate).update(contains("SET status = 'FAILED'"), any(), contains("Refusing"), any());
    }
}
