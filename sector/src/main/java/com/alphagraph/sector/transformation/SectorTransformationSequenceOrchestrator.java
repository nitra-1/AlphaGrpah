package com.alphagraph.sector.transformation;

import com.alphagraph.common.rules.RuleSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Per-instrument try/catch loop, same log-and-continue convention as every Stage 2/3 orchestrator.
 * Drives off {@code SectorContextEvidenceReader.findAllInstrumentIds()} - Stage 1's own real
 * universe, same choice every prior Stage 3 domain made.
 *
 * <p><b>Unlike Financial, {@link #backfill()} is wired to a real, runnable job</b> - Sector's
 * {@code as_of_date} is a real evidence date throughout (never a mere quarter-end label), so a
 * historical replay is point-in-time safe today. This is a distinct concern from the real, disclosed,
 * currently-dormant overwrite risk documented on {@link SectorInflectionHistoryReader} (a *future*
 * risk once {@code reference.sector_benchmarks} populates real, unevenly-paced data for
 * {@code VS_SECTOR}/{@code VS_NIFTY}, not a point-in-time-correctness problem with backfill itself
 * today) - do not conflate the two when reasoning about whether it's safe to run this.
 */
@Component
class SectorTransformationSequenceOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SectorTransformationSequenceOrchestrator.class);
    /** Comfortably covers the 40-session default max sequence age plus buffer for a slower-moving instrument. */
    private static final int HISTORY_LIMIT = 60;

    private final SectorContextEvidenceReader evidenceReader;
    private final SectorInflectionHistoryReader historyReader;
    private final SectorSequenceRuleSetLoader ruleSetLoader;
    private final SectorTransformationSequenceEngine engine;
    private final SectorTransformationSequenceWriter sequenceWriter;
    private final SectorSequenceReadinessWriter readinessWriter;

    SectorTransformationSequenceOrchestrator(
        SectorContextEvidenceReader evidenceReader, SectorInflectionHistoryReader historyReader,
        SectorSequenceRuleSetLoader ruleSetLoader, SectorTransformationSequenceEngine engine,
        SectorTransformationSequenceWriter sequenceWriter, SectorSequenceReadinessWriter readinessWriter
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
                log.warn("Failed to compute sector transformation sequences for instrument {}: {}", instrumentId, e.getMessage());
            }
        }

        log.info("Sector transformation sequence run complete: {} instruments succeeded, {} failed", succeeded, failed);
    }

    /**
     * One-off historical catch-up, not a daily concern - replays {@code evaluateAndWrite} against
     * every real historical {@code as_of_date} an instrument has, each time bounded to that exact
     * date (docs/008 §16's point-in-time rule).
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
                log.warn("Failed to backfill sector transformation sequences for instrument {}: {}", instrumentId, e.getMessage());
            }
        }

        log.info("Sector transformation sequence backfill complete: {} instruments succeeded, {} failed, {} days replayed", succeeded, failed, pointsWritten);
    }

    private void evaluateAndWrite(UUID instrumentId, LocalDate upToInclusiveOrNull, RuleSet rules) {
        List<SectorInflectionHistoryEntry> history = historyReader.findHistory(instrumentId, upToInclusiveOrNull, HISTORY_LIMIT);
        if (history.isEmpty()) {
            return;
        }
        SectorInflectionHistoryEntry latest = history.get(history.size() - 1);
        String symbol = latest.symbol();
        LocalDate asOfDate = latest.asOfDate();

        readinessWriter.write(engine.evaluateReadiness(instrumentId, symbol, asOfDate, history));

        engine.evaluateSectorTailwindSequence(instrumentId, symbol, history, rules).ifPresent(sequenceWriter::write);
        engine.evaluateStockLeadershipEmergence(instrumentId, symbol, history, rules).ifPresent(sequenceWriter::write);
        engine.evaluateIdiosyncraticLeadership(instrumentId, symbol, history, rules).ifPresent(sequenceWriter::write);
    }
}
