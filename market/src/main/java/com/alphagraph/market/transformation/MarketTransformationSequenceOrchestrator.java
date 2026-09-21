package com.alphagraph.market.transformation;

import com.alphagraph.common.rules.RuleSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Per-instrument try/catch loop, same log-and-continue convention as every Stage 2 orchestrator.
 * Drives off {@code MarketTransformationEvidenceReader.findAllInstrumentIds()} - the same real
 * universe Stage 2's own orchestrator already walks. No {@code Clock} field - {@code asOfDate} for
 * a "latest" run is simply the most recent real row in the fetched history, never today's calendar
 * date.
 */
@Component
class MarketTransformationSequenceOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MarketTransformationSequenceOrchestrator.class);
    /** Comfortably covers the 40-session default max sequence age plus buffer for a slower-moving instrument. */
    private static final int HISTORY_LIMIT = 60;

    private final MarketTransformationEvidenceReader evidenceReader;
    private final MarketInflectionHistoryReader historyReader;
    private final MarketSequenceRuleSetLoader ruleSetLoader;
    private final MarketTransformationSequenceEngine engine;
    private final MarketTransformationSequenceWriter sequenceWriter;
    private final MarketSequenceReadinessWriter readinessWriter;

    MarketTransformationSequenceOrchestrator(
        MarketTransformationEvidenceReader evidenceReader, MarketInflectionHistoryReader historyReader,
        MarketSequenceRuleSetLoader ruleSetLoader, MarketTransformationSequenceEngine engine,
        MarketTransformationSequenceWriter sequenceWriter, MarketSequenceReadinessWriter readinessWriter
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
                log.warn("Failed to compute market transformation sequences for instrument {}: {}", instrumentId, e.getMessage());
            }
        }

        log.info("Market transformation sequence run complete: {} instruments succeeded, {} failed", succeeded, failed);
    }

    /**
     * One-off historical catch-up, not a daily concern - replays {@code evaluateAndWrite} against
     * every real historical {@code as_of_date} an instrument has, each time bounded to that exact
     * date (docs/008 §16's point-in-time rule: never using evidence from after the date being
     * reconstructed).
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
                log.warn("Failed to backfill market transformation sequences for instrument {}: {}", instrumentId, e.getMessage());
            }
        }

        log.info("Market transformation sequence backfill complete: {} instruments succeeded, {} failed, {} days replayed", succeeded, failed, pointsWritten);
    }

    private void evaluateAndWrite(UUID instrumentId, LocalDate upToInclusiveOrNull, RuleSet rules) {
        List<MarketInflectionHistoryEntry> history = historyReader.findHistory(instrumentId, upToInclusiveOrNull, HISTORY_LIMIT);
        if (history.isEmpty()) {
            return;
        }
        MarketInflectionHistoryEntry latest = history.get(history.size() - 1);
        String symbol = latest.symbol();
        LocalDate asOfDate = latest.asOfDate();

        readinessWriter.write(engine.evaluateReadiness(instrumentId, symbol, asOfDate, history));

        engine.evaluateDeliveryLedAccumulation(instrumentId, symbol, history, rules).ifPresent(sequenceWriter::write);
        engine.evaluateStealthAccumulationSequence(instrumentId, symbol, history, rules).ifPresent(sequenceWriter::write);
        engine.evaluateMarketRecognitionSequence(instrumentId, symbol, history, rules).ifPresent(sequenceWriter::write);
    }
}
