package com.alphagraph.corporate.transformation;

import com.alphagraph.corporate.actions.CorporateInstrumentLookup;
import com.alphagraph.corporate.api.CorporateActionsReader;
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
 * Per-instrument try/catch loop over every tracked instrument (most will have zero actions of a
 * given type - a real, expected case the engine itself skips, not an error here), same
 * log-and-continue convention as {@code ownership.transformation.OwnershipTransformationOrchestrator}.
 */
@Component
class CapitalAllocationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(CapitalAllocationOrchestrator.class);

    private final CorporateInstrumentLookup instrumentLookup;
    private final CorporateActionsReader actionsReader;
    private final CapitalAllocationEngine engine;
    private final CapitalAllocationEvidenceWriter evidenceWriter;
    private final Clock clock;

    @Autowired
    CapitalAllocationOrchestrator(
        CorporateInstrumentLookup instrumentLookup, CorporateActionsReader actionsReader,
        CapitalAllocationEngine engine, CapitalAllocationEvidenceWriter evidenceWriter
    ) {
        this(instrumentLookup, actionsReader, engine, evidenceWriter, Clock.system(ZoneId.of("Asia/Kolkata")));
    }

    /** Package-private: lets tests inject a fixed Clock instead of depending on the real date. */
    CapitalAllocationOrchestrator(
        CorporateInstrumentLookup instrumentLookup, CorporateActionsReader actionsReader,
        CapitalAllocationEngine engine, CapitalAllocationEvidenceWriter evidenceWriter, Clock clock
    ) {
        this.instrumentLookup = instrumentLookup;
        this.actionsReader = actionsReader;
        this.engine = engine;
        this.evidenceWriter = evidenceWriter;
        this.clock = clock;
    }

    void run() {
        LocalDate asOfDate = LocalDate.now(clock);
        List<UUID> instrumentIds = instrumentLookup.findAllInstrumentIds();

        int succeeded = 0;
        int failed = 0;
        for (UUID instrumentId : instrumentIds) {
            try {
                var actions = actionsReader.findAllActions(instrumentId);
                if (actions.isEmpty()) {
                    continue;
                }
                String symbol = actions.get(0).symbol();
                for (var observation : engine.calculate(instrumentId, symbol, actions, asOfDate)) {
                    evidenceWriter.write(observation);
                }
                succeeded++;
            } catch (Exception e) {
                failed++;
                log.warn("Failed to compute capital allocation evidence for instrument {}: {}", instrumentId, e.getMessage());
            }
        }

        log.info("Capital allocation evidence run complete: {} instruments succeeded, {} failed", succeeded, failed);
    }
}
