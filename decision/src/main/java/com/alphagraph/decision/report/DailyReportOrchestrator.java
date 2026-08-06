package com.alphagraph.decision.report;

import com.alphagraph.decision.api.DailyReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Module 3.6's public entry point: builds one day's evidence, narrates it, and persists the result. */
@Component
public class DailyReportOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(DailyReportOrchestrator.class);

    private final DailyReportEvidenceBuilder evidenceBuilder;
    private final DailyReportClient client;
    private final DailyReportStore store;

    public DailyReportOrchestrator(DailyReportEvidenceBuilder evidenceBuilder, DailyReportClient client, DailyReportStore store) {
        this.evidenceBuilder = evidenceBuilder;
        this.client = client;
        this.store = store;
    }

    public void generateForToday() {
        LocalDate today = LocalDate.now();
        DailyReportEvidence evidence = evidenceBuilder.build(today);
        String narrative = client.narrate(today, evidence.facts());

        DailyReport report = new DailyReport(
            UUID.randomUUID(), today, narrative,
            evidence.topGainerSymbol(), evidence.topGainerRankImprovement(),
            evidence.topDeclinerSymbol(), evidence.topDeclinerRankDecline(),
            evidence.newEventCount(), evidence.guidanceChangeCount(), evidence.positiveNewsCount(), evidence.negativeNewsCount(),
            Instant.now()
        );
        store.write(report);
        log.info("Daily AI Report generated for {}: {} facts, top gainer {}, top decliner {}",
            today, evidence.facts().size(), evidence.topGainerSymbol(), evidence.topDeclinerSymbol());
    }
}
