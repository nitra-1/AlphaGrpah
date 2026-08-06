package com.alphagraph.decision.report;

import java.util.List;

/**
 * Everything {@link DailyReportEvidenceBuilder} computed for one report date - the {@code facts}
 * list feeds {@link DailyReportClient}'s narration, the scalar fields feed
 * decision.api.DailyReport's persisted highlight columns directly (not re-derived from the
 * facts' prose later).
 */
record DailyReportEvidence(
    List<ReportFact> facts,
    String topGainerSymbol, Integer topGainerRankImprovement,
    String topDeclinerSymbol, Integer topDeclinerRankDecline,
    int newEventCount, int guidanceChangeCount, int positiveNewsCount, int negativeNewsCount
) {
}
