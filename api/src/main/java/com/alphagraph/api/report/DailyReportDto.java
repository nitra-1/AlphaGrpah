package com.alphagraph.api.report;

import java.time.Instant;
import java.time.LocalDate;

public record DailyReportDto(
    LocalDate reportDate, String narrative,
    String topGainerSymbol, Integer topGainerRankImprovement,
    String topDeclinerSymbol, Integer topDeclinerRankDecline,
    int newEventCount, int guidanceChangeCount, int positiveNewsCount, int negativeNewsCount,
    Instant generatedAt
) {
}
