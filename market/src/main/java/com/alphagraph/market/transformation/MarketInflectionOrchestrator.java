package com.alphagraph.market.transformation;

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
 * Per-instrument try/catch loop, same log-and-continue convention as
 * {@code ownership.transformation.OwnershipTransformationOrchestrator}. Drives off
 * {@code MarketTransformationEvidenceReader.findAllInstrumentIds()} - every instrument with at
 * least one real Stage 1 evidence row, not {@code market.daily_prices}' raw universe.
 */
@Component
class MarketInflectionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MarketInflectionOrchestrator.class);

    private final MarketTransformationEvidenceReader evidenceReader;
    private final MarketInflectionStateReader stateReader;
    private final MarketInflectionEngine engine;
    private final MarketInflectionWriter writer;
    private final Clock clock;

    @Autowired
    MarketInflectionOrchestrator(
        MarketTransformationEvidenceReader evidenceReader, MarketInflectionStateReader stateReader,
        MarketInflectionEngine engine, MarketInflectionWriter writer
    ) {
        this(evidenceReader, stateReader, engine, writer, Clock.system(ZoneId.of("Asia/Kolkata")));
    }

    /** Package-private: lets tests inject a fixed Clock instead of depending on the real date. */
    MarketInflectionOrchestrator(
        MarketTransformationEvidenceReader evidenceReader, MarketInflectionStateReader stateReader,
        MarketInflectionEngine engine, MarketInflectionWriter writer, Clock clock
    ) {
        this.evidenceReader = evidenceReader;
        this.stateReader = stateReader;
        this.engine = engine;
        this.writer = writer;
        this.clock = clock;
    }

    void run() {
        LocalDate asOfDate = LocalDate.now(clock);
        List<UUID> instrumentIds = evidenceReader.findAllInstrumentIds();

        int succeeded = 0;
        int failed = 0;
        for (UUID instrumentId : instrumentIds) {
            try {
                var delivery = evidenceReader.findLatest(instrumentId, MarketMetric.DELIVERY_PERCENTAGE_20D_AVG).orElse(null);
                var relativeVolume = evidenceReader.findLatest(instrumentId, MarketMetric.RELATIVE_VOLUME).orElse(null);
                var priceReturn = evidenceReader.findLatest(instrumentId, MarketMetric.PRICE_RETURN_20D).orElse(null);
                if (delivery == null && relativeVolume == null && priceReturn == null) {
                    continue;
                }
                String symbol = firstSymbol(delivery, relativeVolume, priceReturn);
                String priorTradingState = stateReader.findPriorTradingState(instrumentId, asOfDate).orElse(null);

                MarketInflectionResult result = engine.calculate(instrumentId, symbol, asOfDate, delivery, relativeVolume, priceReturn, priorTradingState);
                writer.write(result);
                succeeded++;
            } catch (Exception e) {
                failed++;
                log.warn("Failed to compute market inflection state for instrument {}: {}", instrumentId, e.getMessage());
            }
        }

        log.info("Market inflection run complete: {} instruments succeeded, {} failed", succeeded, failed);
    }

    private static String firstSymbol(MarketEvidenceObservation... observations) {
        for (MarketEvidenceObservation observation : observations) {
            if (observation != null) {
                return observation.symbol();
            }
        }
        throw new IllegalStateException("No observation carried a symbol");
    }
}
