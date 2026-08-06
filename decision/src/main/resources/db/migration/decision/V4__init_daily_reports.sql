-- Module 3.6: Daily AI Report - a once-daily synthesized digest, not a per-request live view
-- (unlike Module 3.4's Portfolio Risk): every request would otherwise mean a real Claude API
-- call and could produce a different narrative for the same day. Generated once by
-- decision.report.DailyReportOrchestrator (22:00 IST, after DecisionScoringScheduler), then
-- served from storage. narrative is Claude's synthesized summary of the day's deterministic
-- facts; the scalar highlight columns are the same facts pulled out for a UI to render a quick
-- header without re-parsing prose - the underlying detail (which events, which news) is already
-- independently queryable via the existing corporate/dashboard endpoints, so this table
-- deliberately doesn't duplicate it as a JSON blob.
CREATE TABLE decision.daily_reports (
    id                          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    report_date                 date NOT NULL,
    narrative                   text NOT NULL,
    top_gainer_symbol           varchar(20),
    top_gainer_rank_improvement integer,
    top_decliner_symbol         varchar(20),
    top_decliner_rank_decline   integer,
    new_event_count             integer NOT NULL DEFAULT 0,
    guidance_change_count       integer NOT NULL DEFAULT 0,
    positive_news_count         integer NOT NULL DEFAULT 0,
    negative_news_count         integer NOT NULL DEFAULT 0,
    generated_at                timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_daily_reports_report_date UNIQUE (report_date)
);

CREATE INDEX ix_daily_reports_report_date ON decision.daily_reports (report_date);
