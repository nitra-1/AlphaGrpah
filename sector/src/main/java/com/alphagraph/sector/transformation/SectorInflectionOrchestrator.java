package com.alphagraph.sector.transformation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Per-instrument try/catch loop, same log-and-continue convention as every other family's Stage 2
 * orchestrator. No {@code Clock} dependency - {@code asOfDate} is derived entirely from the
 * evidence itself (see {@code SectorInflectionEngine}'s javadoc for why: only
 * {@code SECTOR_RELATIVE_STRENGTH} is reliably daily-cadence, unlike Market's 3 metrics which all
 * genuinely are).
 */
@Component
class SectorInflectionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SectorInflectionOrchestrator.class);

    private final SectorContextEvidenceReader evidenceReader;
    private final SectorInflectionEngine engine;
    private final SectorInflectionWriter writer;

    SectorInflectionOrchestrator(SectorContextEvidenceReader evidenceReader, SectorInflectionEngine engine, SectorInflectionWriter writer) {
        this.evidenceReader = evidenceReader;
        this.engine = engine;
        this.writer = writer;
    }

    void run() {
        List<UUID> instrumentIds = evidenceReader.findAllInstrumentIds();

        int succeeded = 0;
        int failed = 0;
        for (UUID instrumentId : instrumentIds) {
            try {
                var sectorRelativeStrength = evidenceReader.findLatest(instrumentId, SectorMetric.SECTOR_RELATIVE_STRENGTH).orElse(null);
                var vsNifty = evidenceReader.findLatest(instrumentId, SectorMetric.INSTRUMENT_RELATIVE_STRENGTH_VS_NIFTY).orElse(null);
                var vsSector = evidenceReader.findLatest(instrumentId, SectorMetric.INSTRUMENT_RELATIVE_STRENGTH_VS_SECTOR).orElse(null);
                if (sectorRelativeStrength == null && vsNifty == null && vsSector == null) {
                    continue;
                }
                String symbol = firstSymbol(sectorRelativeStrength, vsNifty, vsSector);
                int vsNiftyObservationCount = evidenceReader.countObservations(instrumentId, SectorMetric.INSTRUMENT_RELATIVE_STRENGTH_VS_NIFTY);
                int vsSectorObservationCount = evidenceReader.countObservations(instrumentId, SectorMetric.INSTRUMENT_RELATIVE_STRENGTH_VS_SECTOR);

                SectorInflectionResult result = engine.calculate(
                    instrumentId, symbol, sectorRelativeStrength, vsNifty, vsSector, vsNiftyObservationCount, vsSectorObservationCount
                );
                writer.write(result);
                succeeded++;
            } catch (Exception e) {
                failed++;
                log.warn("Failed to compute sector inflection state for instrument {}: {}", instrumentId, e.getMessage());
            }
        }

        log.info("Sector inflection run complete: {} instruments succeeded, {} failed", succeeded, failed);
    }

    private static String firstSymbol(SectorEvidenceObservation... observations) {
        for (SectorEvidenceObservation observation : observations) {
            if (observation != null) {
                return observation.symbol();
            }
        }
        throw new IllegalStateException("No observation carried a symbol");
    }
}
