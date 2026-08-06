package com.alphagraph.decision.report;

import com.alphagraph.decision.api.DailyReport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.UUID;

/** Writes new {@link DailyReport}s - one row per report_date, upserting on a same-day re-run, mirroring every prior Layer 2 snapshot store. */
@Component
class DailyReportStore {

    private final JdbcTemplate jdbcTemplate;

    DailyReportStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void write(DailyReport report) {
        jdbcTemplate.update(
            """
            INSERT INTO decision.daily_reports (
                id, report_date, narrative, top_gainer_symbol, top_gainer_rank_improvement,
                top_decliner_symbol, top_decliner_rank_decline, new_event_count, guidance_change_count,
                positive_news_count, negative_news_count, generated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (report_date) DO UPDATE SET
                narrative = EXCLUDED.narrative,
                top_gainer_symbol = EXCLUDED.top_gainer_symbol,
                top_gainer_rank_improvement = EXCLUDED.top_gainer_rank_improvement,
                top_decliner_symbol = EXCLUDED.top_decliner_symbol,
                top_decliner_rank_decline = EXCLUDED.top_decliner_rank_decline,
                new_event_count = EXCLUDED.new_event_count,
                guidance_change_count = EXCLUDED.guidance_change_count,
                positive_news_count = EXCLUDED.positive_news_count,
                negative_news_count = EXCLUDED.negative_news_count,
                generated_at = EXCLUDED.generated_at
            """,
            UUID.randomUUID(), Date.valueOf(report.reportDate()), report.narrative(),
            report.topGainerSymbol(), report.topGainerRankImprovement(),
            report.topDeclinerSymbol(), report.topDeclinerRankDecline(),
            report.newEventCount(), report.guidanceChangeCount(), report.positiveNewsCount(), report.negativeNewsCount(),
            Timestamp.from(report.generatedAt())
        );
    }
}
