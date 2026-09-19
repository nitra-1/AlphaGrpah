package com.alphagraph.corporate.transformation;

import com.alphagraph.corporate.actions.CorporateInstrumentLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Per-instrument try/catch loop, same log-and-continue convention as every other family's Stage 2
 * orchestrator. Reuses {@link CorporateInstrumentLookup#findAllInstrumentIds()} - the exact same
 * universe Stage 1's own {@code CapitalAllocationOrchestrator} already walks - rather than adding a
 * second, redundant "list all instruments" query. {@code Clock}-based {@code asOfDate}, unlike
 * Financial's quarterly-cadence family - see {@code CapitalAllocationInflectionResult}'s javadoc.
 */
@Component
class CapitalAllocationInflectionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(CapitalAllocationInflectionOrchestrator.class);

    private final CorporateInstrumentLookup instrumentLookup;
    private final CapitalAllocationEvidenceReader evidenceReader;
    private final CapitalAllocationInflectionEngine engine;
    private final CapitalAllocationInflectionWriter writer;
    private final Clock clock;

    @Autowired
    CapitalAllocationInflectionOrchestrator(
        CorporateInstrumentLookup instrumentLookup, CapitalAllocationEvidenceReader evidenceReader,
        CapitalAllocationInflectionEngine engine, CapitalAllocationInflectionWriter writer
    ) {
        this(instrumentLookup, evidenceReader, engine, writer, Clock.system(ZoneId.of("Asia/Kolkata")));
    }

    /** Package-private: lets tests inject a fixed Clock instead of depending on the real date. */
    CapitalAllocationInflectionOrchestrator(
        CorporateInstrumentLookup instrumentLookup, CapitalAllocationEvidenceReader evidenceReader,
        CapitalAllocationInflectionEngine engine, CapitalAllocationInflectionWriter writer, Clock clock
    ) {
        this.instrumentLookup = instrumentLookup;
        this.evidenceReader = evidenceReader;
        this.engine = engine;
        this.writer = writer;
        this.clock = clock;
    }

    void run() {
        LocalDate asOfDate = LocalDate.now(clock);
        List<UUID> instrumentIds = instrumentLookup.findAllInstrumentIds();

        int succeeded = 0;
        int failed = 0;
        for (UUID instrumentId : instrumentIds) {
            try {
                var buyback = evidenceReader.findLatest(instrumentId, CapitalAllocationMetric.BUYBACK_EVENT_COUNT_180D).orElse(null);
                var equityRaise = evidenceReader.findLatest(instrumentId, CapitalAllocationMetric.EQUITY_RAISE_EVENT_COUNT_180D).orElse(null);
                if (buyback == null && equityRaise == null) {
                    continue;
                }
                String symbol = buyback != null ? buyback.symbol() : equityRaise.symbol();

                CapitalAllocationInflectionResult result = engine.calculate(instrumentId, symbol, asOfDate, buyback, equityRaise);
                writer.write(result);
                succeeded++;
            } catch (Exception e) {
                failed++;
                log.warn("Failed to compute capital allocation inflection state for instrument {}: {}", instrumentId, e.getMessage());
            }
        }

        log.info("Capital allocation inflection run complete: {} instruments succeeded, {} failed", succeeded, failed);
    }
}
