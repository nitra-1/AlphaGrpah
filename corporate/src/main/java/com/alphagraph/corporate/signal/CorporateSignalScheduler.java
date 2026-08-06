package com.alphagraph.corporate.signal;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs 15 minutes after {@code NewsCatalystScheduler} (Module 2.6, 19:30 IST) - by then all four
 * corporate engines this module combines have had their own chance to run for the day.
 */
@Component
public class CorporateSignalScheduler {

    private static final String CRON_745PM_IST = "0 45 19 * * *";

    private final CorporateSignalOrchestrator orchestrator;

    public CorporateSignalScheduler(CorporateSignalOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Scheduled(cron = CRON_745PM_IST, zone = "Asia/Kolkata")
    public void runCorporateSignalUpdate() {
        orchestrator.recomputeAllInstruments();
    }
}
