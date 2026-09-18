package com.alphagraph.ownership.transformation;

import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.ownership.engine.BulkDealsReader;
import com.alphagraph.ownership.engine.ShareholdingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
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

    /**
     * One-off historical catch-up, not a daily concern - replays {@link OwnershipTransformationEngine#calculate}
     * unchanged against every real quarter transition in a symbol's full shareholding history
     * (up to 21 for a symbol with 22 real quarters), not just the newest. Deliberately writes only
     * {@code calculation.evidence()} via the existing append-only {@link TransformationEvidenceWriter}
     * - never calls {@link OwnershipTransformationWriter#write}, since {@code transformation_states}
     * is explicitly a "latest state today" upsert table, not a historical ledger; replaying old
     * states into it would just be overwritten by the real daily run's own state anyway.
     */
    void backfill() {
        RuleSet rules = ruleSetLoader.loadActiveRules();
        List<UUID> instrumentIds = shareholdingReader.instrumentIdsWithShareholdingData();

        int succeeded = 0;
        int failed = 0;
        int transitionsWritten = 0;
        for (UUID instrumentId : instrumentIds) {
            try {
                var periods = historyReader.findPeriods(instrumentId);
                var bulkDeals = bulkDealsReader.findRecentDeals(instrumentId);
                for (int i = 1; i < periods.size(); i++) {
                    var periodsThroughQuarter = periods.subList(0, i + 1);
                    Optional<TransformationCalculation> calculation = engine.calculate(periodsThroughQuarter, bulkDeals, rules);
                    if (calculation.isEmpty()) {
                        continue;
                    }
                    for (EvidenceObservation observation : calculation.get().evidence()) {
                        evidenceWriter.write(observation, rules.version());
                    }
                    transitionsWritten++;
                }
                succeeded++;
            } catch (Exception e) {
                failed++;
                log.warn("Failed to backfill ownership transformation for instrument {}: {}", instrumentId, e.getMessage());
            }
        }

        log.info("Ownership transformation backfill complete: {} instruments succeeded, {} failed, {} quarter transitions replayed", succeeded, failed, transitionsWritten);
    }
}
