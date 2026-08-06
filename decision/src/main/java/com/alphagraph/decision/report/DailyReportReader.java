package com.alphagraph.decision.report;

import com.alphagraph.decision.api.DailyReport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class DailyReportReader {

    private static final RowMapper<DailyReport> ROW_MAPPER = (rs, rowNum) -> new DailyReport(
        (UUID) rs.getObject("id"), rs.getDate("report_date").toLocalDate(), rs.getString("narrative"),
        rs.getString("top_gainer_symbol"), (Integer) rs.getObject("top_gainer_rank_improvement"),
        rs.getString("top_decliner_symbol"), (Integer) rs.getObject("top_decliner_rank_decline"),
        rs.getInt("new_event_count"), rs.getInt("guidance_change_count"),
        rs.getInt("positive_news_count"), rs.getInt("negative_news_count"),
        rs.getTimestamp("generated_at").toInstant()
    );

    private static final String SELECT_COLUMNS = """
        id, report_date, narrative, top_gainer_symbol, top_gainer_rank_improvement,
        top_decliner_symbol, top_decliner_rank_decline, new_event_count, guidance_change_count,
        positive_news_count, negative_news_count, generated_at
        """;

    private final JdbcTemplate jdbcTemplate;

    public DailyReportReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<DailyReport> findLatest() {
        List<DailyReport> rows = jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " FROM decision.daily_reports ORDER BY report_date DESC LIMIT 1",
            ROW_MAPPER
        );
        return rows.stream().findFirst();
    }

    public Optional<DailyReport> findByDate(LocalDate reportDate) {
        List<DailyReport> rows = jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " FROM decision.daily_reports WHERE report_date = ?",
            ROW_MAPPER, Date.valueOf(reportDate)
        );
        return rows.stream().findFirst();
    }
}
