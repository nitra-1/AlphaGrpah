package com.alphagraph.decision.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One day's synthesized digest (Module 3.6) - narrative is Claude's summary of the day's
 * deterministic facts (decision.report.DailyReportEvidenceBuilder), never itself the source of
 * any number quoted in it. The scalar highlight fields are the same underlying facts pulled out
 * separately (not re-parsed from narrative) so a client can render a quick header without
 * depending on prose structure. Highlight fields are null when there was no prior day to diff
 * against (e.g. the very first report) or nothing notable happened.
 */
public record DailyReport(
    UUID id, LocalDate reportDate, String narrative,
    String topGainerSymbol, Integer topGainerRankImprovement,
    String topDeclinerSymbol, Integer topDeclinerRankDecline,
    int newEventCount, int guidanceChangeCount, int positiveNewsCount, int negativeNewsCount,
    Instant generatedAt
) {
}
