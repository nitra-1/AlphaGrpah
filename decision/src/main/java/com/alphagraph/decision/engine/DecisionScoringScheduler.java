package com.alphagraph.decision.engine;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs after every upstream scheduler that feeds a domain score has had its chance to run for the
 * day - the latest is {@code corporate.news.NewsCatalystScheduler} at 21:30 IST, itself feeding
 * {@code corporate.signal.CorporateSignalScheduler} at 19:45 IST (before the News Catalyst score
 * it depends on refreshes - a pre-existing sequencing quirk in that module, not something this
 * scheduler can fix). 21:45 IST leaves 15 minutes after the last of them.
 */
@Component
public class DecisionScoringScheduler {

    private static final String CRON_945PM_IST = "0 45 21 * * *";

    private final DecisionScoringOrchestrator orchestrator;

    public DecisionScoringScheduler(DecisionScoringOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Scheduled(cron = CRON_945PM_IST, zone = "Asia/Kolkata")
    public void runDecisionScoringUpdate() {
        orchestrator.recomputeAllInstruments();
    }
}
