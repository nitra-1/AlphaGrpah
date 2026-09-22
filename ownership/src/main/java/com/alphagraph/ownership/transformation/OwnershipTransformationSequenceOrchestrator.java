package com.alphagraph.ownership.transformation;

import com.alphagraph.common.rules.RuleSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Per-instrument try/catch loop, same log-and-continue convention as every Stage 2/3 orchestrator.
 * Drives off {@code OwnershipTransformationEvidenceReader.findAllInstrumentIds()} - Stage 1's own
 * real universe, same choice Market's Stage 3 made.
 */
@Component
class OwnershipTransformationSequenceOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OwnershipTransformationSequenceOrchestrator.class);
    /** Comfortably covers the 6-period default max sequence age plus buffer for a deep-history instrument (avg 19.3 promoter/public transitions). */
    private static final int HISTORY_LIMIT = 30;

    private final OwnershipTransformationEvidenceReader evidenceReader;
    private final OwnershipInflectionHistoryReader historyReader;
    private final OwnershipSequenceRuleSetLoader ruleSetLoader;
    private final OwnershipTransformationSequenceEngine engine;
    private final OwnershipTransformationSequenceWriter sequenceWriter;
    private final OwnershipSequenceReadinessWriter readinessWriter;

    OwnershipTransformationSequenceOrchestrator(
        OwnershipTransformationEvidenceReader evidenceReader, OwnershipInflectionHistoryReader historyReader,
        OwnershipSequenceRuleSetLoader ruleSetLoader, OwnershipTransformationSequenceEngine engine,
        OwnershipTransformationSequenceWriter sequenceWriter, OwnershipSequenceReadinessWriter readinessWriter
    ) {
        this.evidenceReader = evidenceReader;
        this.historyReader = historyReader;
        this.ruleSetLoader = ruleSetLoader;
        this.engine = engine;
        this.sequenceWriter = sequenceWriter;
        this.readinessWriter = readinessWriter;
    }

    void run() {
        RuleSet rules = ruleSetLoader.loadActiveRules();
        List<UUID> instrumentIds = evidenceReader.findAllInstrumentIds();

        int succeeded = 0;
        int failed = 0;
        for (UUID instrumentId : instrumentIds) {
            try {
                evaluateAndWrite(instrumentId, null, rules);
                succeeded++;
            } catch (Exception e) {
                failed++;
                log.warn("Failed to compute ownership transformation sequences for instrument {}: {}", instrumentId, e.getMessage());
            }
        }

        log.info("Ownership transformation sequence run complete: {} instruments succeeded, {} failed", succeeded, failed);
    }

    /**
     * One-off historical catch-up, not a daily concern - replays {@code evaluateAndWrite} against
     * every real distinct quarter an instrument has (docs/008 §16's point-in-time rule: never using
     * evidence from after the quarter being reconstructed).
     */
    void backfill() {
        RuleSet rules = ruleSetLoader.loadActiveRules();
        List<UUID> instrumentIds = evidenceReader.findAllInstrumentIds();

        int succeeded = 0;
        int failed = 0;
        int pointsWritten = 0;
        for (UUID instrumentId : instrumentIds) {
            try {
                List<LocalDate> realDates = historyReader.findAllAsOfDates(instrumentId);
                for (LocalDate asOfDate : realDates) {
                    evaluateAndWrite(instrumentId, asOfDate, rules);
                    pointsWritten++;
                }
                succeeded++;
            } catch (Exception e) {
                failed++;
                log.warn("Failed to backfill ownership transformation sequences for instrument {}: {}", instrumentId, e.getMessage());
            }
        }

        log.info("Ownership transformation sequence backfill complete: {} instruments succeeded, {} failed, {} quarters replayed", succeeded, failed, pointsWritten);
    }

    private void evaluateAndWrite(UUID instrumentId, LocalDate upToInclusiveOrNull, RuleSet rules) {
        List<OwnershipInflectionHistoryEntry> history = historyReader.findHistory(instrumentId, upToInclusiveOrNull, HISTORY_LIMIT);
        if (history.isEmpty()) {
            return;
        }
        OwnershipInflectionHistoryEntry latest = history.get(history.size() - 1);
        String symbol = latest.symbol();
        LocalDate asOfDate = latest.asOfDate();

        readinessWriter.write(engine.evaluateReadiness(instrumentId, symbol, asOfDate, history));

        engine.evaluateInstitutionalOwnershipBuilding(instrumentId, symbol, history, rules).ifPresent(sequenceWriter::write);
        engine.evaluateBroadInstitutionalParticipation(instrumentId, symbol, history, rules).ifPresent(sequenceWriter::write);
        engine.evaluatePromoterInstitutionAlignment(instrumentId, symbol, history, rules).ifPresent(sequenceWriter::write);
    }
}
