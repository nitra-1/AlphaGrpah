package com.alphagraph.market.transformation;

import com.alphagraph.market.api.DailyPrice;
import com.alphagraph.market.api.DailyPriceReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Per-instrument try/catch loop, same log-and-continue convention as
 * {@code ownership.transformation.OwnershipTransformationOrchestrator}. Drives off
 * {@code market.api.DailyPriceReader.instrumentIdsWithHistory()} unmodified - a read-only call to
 * an existing public method, not an edit to that class.
 */
@Component
class MarketAccumulationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MarketAccumulationOrchestrator.class);

    private final DailyPriceReader priceReader;
    private final MarketAccumulationEngine engine;
    private final MarketTransformationEvidenceWriter evidenceWriter;

    MarketAccumulationOrchestrator(DailyPriceReader priceReader, MarketAccumulationEngine engine, MarketTransformationEvidenceWriter evidenceWriter) {
        this.priceReader = priceReader;
        this.engine = engine;
        this.evidenceWriter = evidenceWriter;
    }

    void run() {
        List<UUID> instrumentIds = priceReader.instrumentIdsWithHistory();

        int succeeded = 0;
        int failed = 0;
        for (UUID instrumentId : instrumentIds) {
            try {
                List<DailyPrice> prices = priceReader.findHistory(instrumentId);
                if (prices.isEmpty()) {
                    continue;
                }
                String symbol = prices.get(prices.size() - 1).symbol();
                for (MarketEvidenceObservation observation : engine.calculate(instrumentId, symbol, prices)) {
                    evidenceWriter.write(observation);
                }
                succeeded++;
            } catch (Exception e) {
                failed++;
                log.warn("Failed to compute market accumulation evidence for instrument {}: {}", instrumentId, e.getMessage());
            }
        }

        log.info("Market accumulation evidence run complete: {} instruments succeeded, {} failed", succeeded, failed);
    }
}
