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
 * A ceiling on how much exchange-announcement text is sent to Claude in one India calendar day. On
 * a results-season day a flood of large filings (24 Oct: 49 announcements, 3.3M characters,
 * ~$9) had no limit at all. The text length of each document is charged once when it's about to be
 * extracted; once the day's allowance is spent the rest are deferred to the next sweep (they stay
 * {@code PROCESSED}, oldest first - nothing is dropped). A big filing therefore delays the
 * remainder by a day or two rather than costing a spike.
 *
 * <p>The first document of a day is always allowed even if it alone exceeds the allowance (it would
 * otherwise never run), and a limit of 0 or less disables the cap. Charging is one atomic upsert,
 * kept in {@code corporate.claude_text_usage} so a restart can't reset the day's total.
 */
@Component
class AnnouncementTextBudget {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private final JdbcTemplate jdbcTemplate;
    private final long dailyCharLimit;
    private final Clock clock;

    @Autowired
    AnnouncementTextBudget(
        JdbcTemplate jdbcTemplate,
        @Value("${alphagraph.corporate.announcement-text-budget.daily-chars:1500000}") long dailyCharLimit
    ) {
        this(jdbcTemplate, dailyCharLimit, Clock.system(INDIA));
    }

    AnnouncementTextBudget(JdbcTemplate jdbcTemplate, long dailyCharLimit, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.dailyCharLimit = dailyCharLimit;
        this.clock = clock;
    }

    /** True if a document of {@code chars} characters may be extracted today (and its length is now charged). */
    boolean tryCharge(String source, long chars) {
        if (dailyCharLimit <= 0) {
            return true;
        }
        LocalDate today = LocalDate.now(clock.withZone(INDIA));
        List<Long> charged = jdbcTemplate.query(
            """
            INSERT INTO corporate.claude_text_usage (usage_date, source, chars_used) VALUES (?, ?, ?)
            ON CONFLICT (usage_date, source) DO UPDATE SET chars_used = corporate.claude_text_usage.chars_used + EXCLUDED.chars_used
            WHERE corporate.claude_text_usage.chars_used + EXCLUDED.chars_used <= ?
            RETURNING chars_used
            """,
            (rs, rowNum) -> rs.getLong(1), java.sql.Date.valueOf(today), source, chars, dailyCharLimit
        );
        return !charged.isEmpty();
    }
}
