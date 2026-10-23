-- How many times per day each news step fell back from Gemini to Claude. Two jobs: it is the
-- counter behind the daily fallback cap (corporate.knowledge.ClaudeFallbackBudget - kept in the
-- database rather than memory so an app restart can't hand back a fresh allowance), and it is the
-- first record of how much of the news pipeline lands on Claude at all - until now nothing stored
-- which provider answered a call. Query it directly:
--   SELECT * FROM corporate.claude_fallback_usage ORDER BY usage_date DESC, purpose;
CREATE TABLE corporate.claude_fallback_usage (
    usage_date  date        NOT NULL,
    purpose     varchar(40) NOT NULL,
    call_count  integer     NOT NULL,
    PRIMARY KEY (usage_date, purpose)
);
