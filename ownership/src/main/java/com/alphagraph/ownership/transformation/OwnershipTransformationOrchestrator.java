package com.alphagraph.ownership.transformation;

import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.ownership.engine.BulkDealsReader;
import com.alphagraph.ownership.engine.ShareholdingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Per-instrument try/catch loop, same log-and-continue convention as
 * {@code ownership.interpretation.InstitutionalInterpretationOrchestrator}. Drives off
 * {@code ownership.engine.ShareholdingReader.instrumentIdsWithShareholdingData()} unmodified - a
 * read-only call to an existing public method, not an edit to that class.
 */
@Component
class OwnershipTransformationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OwnershipTransformationOrchestrator.class);

    private final ShareholdingReader shareholdingReader;
    private final TransformationHistoryReader historyReader;
    private final BulkDealsReader bulkDealsReader;
    private final OwnershipTransformationRuleSetLoader ruleSetLoader;
    private final OwnershipTransformationEngine engine;
    private final TransformationEvidenceWriter evidenceWriter;
    private final OwnershipTransformationWriter stateWriter;

    OwnershipTransformationOrchestrator(
        ShareholdingReader shareholdingReader, TransformationHistoryReader historyReader, BulkDealsReader bulkDealsReader,
        OwnershipTransformationRuleSetLoader ruleSetLoader, OwnershipTransformationEngine engine,
        TransformationEvidenceWriter evidenceWriter, OwnershipTransformationWriter stateWriter
    ) {
        this.shareholdingReader = shareholdingReader;
        this.historyReader = historyReader;
        this.bulkDealsReader = bulkDealsReader;
        this.ruleSetLoader = ruleSetLoader;
        this.engine = engine;
        this.evidenceWriter = evidenceWriter;
        this.stateWriter = stateWriter;
    }

    void run() {
        RuleSet rules = ruleSetLoader.loadActiveRules();
        List<UUID> instrumentIds = shareholdingReader.instrumentIdsWithShareholdingData();

        int succeeded = 0;
        int failed = 0;
        for (UUID instrumentId : instrumentIds) {
            try {
                var periods = historyReader.findPeriods(instrumentId);
                var bulkDeals = bulkDealsReader.findRecentDeals(instrumentId);
                engine.calculate(periods, bulkDeals, rules).ifPresent(calculation -> {
                    for (EvidenceObservation observation : calculation.evidence()) {
                        evidenceWriter.write(observation, rules.version());
                    }
                    stateWriter.write(calculation.result());
                });
                succeeded++;
            } catch (Exception e) {
                failed++;
                log.warn("Failed to compute ownership transformation for instrument {}: {}", instrumentId, e.getMessage());
            }
        }

        log.info("Ownership transformation run complete: {} instruments succeeded, {} failed", succeeded, failed);
    }
}
