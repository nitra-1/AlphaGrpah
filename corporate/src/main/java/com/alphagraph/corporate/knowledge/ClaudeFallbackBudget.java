package com.alphagraph.corporate.knowledge;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * The daily allowance for falling back from Gemini to Claude on a news document - the thing that
 * keeps Claude spend predictable. One counter per {@code purpose} (Stage 1 classification and news
 * extraction are budgeted separately) per India-time day, in {@code
 * corporate.claude_fallback_usage}. Taking a slot is a single atomic upsert: it only succeeds
 * while the count is under the limit, so concurrent sweeps can't overshoot it.
 *
 * <p>A limit of 0 (or less) disables the fallback entirely - a failed Gemini call then always
 * defers the document to the next sweep. A slot is consumed when taken, even if the Claude call
 * that follows fails; the cap bounds attempts, not successes.
 */
@Component
class ClaudeFallbackBudget {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private final JdbcTemplate jdbcTemplate;
    private final int dailyLimitPerPurpose;
    private final Clock clock;

    @Autowired
    ClaudeFallbackBudget(
        JdbcTemplate jdbcTemplate,
        @Value("${alphagraph.corporate.claude-fallback.daily-limit-per-purpose:10}") int dailyLimitPerPurpose
    ) {
        this(jdbcTemplate, dailyLimitPerPurpose, Clock.system(INDIA));
    }

    ClaudeFallbackBudget(JdbcTemplate jdbcTemplate, int dailyLimitPerPurpose, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.dailyLimitPerPurpose = dailyLimitPerPurpose;
        this.clock = clock;
    }

    /** True if the caller may make one Claude fallback call for {@code purpose} today (and the slot is now spent). */
    boolean tryAcquire(String purpose) {
        if (dailyLimitPerPurpose <= 0) {
            return false;
        }
        LocalDate today = LocalDate.now(clock.withZone(INDIA));
        List<Integer> granted = jdbcTemplate.query(
            """
            INSERT INTO corporate.claude_fallback_usage (usage_date, purpose, call_count) VALUES (?, ?, 1)
            ON CONFLICT (usage_date, purpose) DO UPDATE SET call_count = corporate.claude_fallback_usage.call_count + 1
            WHERE corporate.claude_fallback_usage.call_count < ?
            RETURNING call_count
            """,
            (rs, rowNum) -> rs.getInt(1), java.sql.Date.valueOf(today), purpose, dailyLimitPerPurpose
        );
        return !granted.isEmpty();
    }
}
