package com.alphagraph.api.report;

import com.alphagraph.decision.api.DailyReport;
import com.alphagraph.decision.report.DailyReportReader;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

@Service
public class DailyReportViewService {

    private final DailyReportReader reader;

    public DailyReportViewService(DailyReportReader reader) {
        this.reader = reader;
    }

    public Optional<DailyReportDto> latest() {
        return reader.findLatest().map(DailyReportViewService::toDto);
    }

    public Optional<DailyReportDto> byDate(LocalDate reportDate) {
        return reader.findByDate(reportDate).map(DailyReportViewService::toDto);
    }

    private static DailyReportDto toDto(DailyReport r) {
        return new DailyReportDto(
            r.reportDate(), r.narrative(),
            r.topGainerSymbol(), r.topGainerRankImprovement(),
            r.topDeclinerSymbol(), r.topDeclinerRankDecline(),
            r.newEventCount(), r.guidanceChangeCount(), r.positiveNewsCount(), r.negativeNewsCount(),
            r.generatedAt()
        );
    }
}
