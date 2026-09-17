package com.alphagraph.financial.transformation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fetches once (the collector already does its own per-symbol paced/retried loop internally, same
 * as {@code ownership.pattern.HttpShareholdingCollector}), then groups the combined quarters by
 * symbol and runs the engine per symbol. Two separate try/catch levels, both log-and-continue
 * matching {@code ownership.transformation.OwnershipTransformationOrchestrator}'s convention: one
 * per raw quarter during normalization (a single bad date/amount must never abort every other
 * already-fetched quarter - found live, see claude.md), one per symbol during compute/write.
 */
@Component
class FinancialTransformationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(FinancialTransformationOrchestrator.class);

    private final HttpResultsComparisionCollector collector;
    private final ResultsComparisionParser parser;
    private final FinancialResultsComparisionNormalizer normalizer;
    private final FinancialTransformationEngine engine;
    private final FinancialTransformationEvidenceWriter evidenceWriter;

    FinancialTransformationOrchestrator(
        HttpResultsComparisionCollector collector, ResultsComparisionParser parser,
        FinancialResultsComparisionNormalizer normalizer, FinancialTransformationEngine engine,
        FinancialTransformationEvidenceWriter evidenceWriter
    ) {
        this.collector = collector;
        this.parser = parser;
        this.normalizer = normalizer;
        this.engine = engine;
        this.evidenceWriter = evidenceWriter;
    }

    void run() {
        String rawJson = collector.fetch();
        List<RawResultsComparisionRow> rawRows = parser.parse(rawJson);

        Map<String, List<FinancialResultsPeriod>> periodsBySymbol = new LinkedHashMap<>();
        int normalizeFailed = 0;
        for (RawResultsComparisionRow raw : rawRows) {
            try {
                normalizer.normalize(raw).ifPresent(period ->
                    periodsBySymbol.computeIfAbsent(period.symbol(), s -> new ArrayList<>()).add(period)
                );
            } catch (Exception e) {
                normalizeFailed++;
                log.warn("Failed to normalize a results-comparison quarter for symbol {}: {}", raw.symbol(), e.getMessage());
            }
        }

        int succeeded = 0;
        int failed = 0;
        for (Map.Entry<String, List<FinancialResultsPeriod>> entry : periodsBySymbol.entrySet()) {
            try {
                List<FinancialResultsPeriod> ascending = entry.getValue().stream()
                    .sorted(java.util.Comparator.comparing(FinancialResultsPeriod::periodEnd))
                    .toList();
                for (FinancialEvidenceObservation observation : engine.calculate(ascending)) {
                    evidenceWriter.write(observation);
                }
                succeeded++;
            } catch (Exception e) {
                failed++;
                log.warn("Failed to compute financial transformation for symbol {}: {}", entry.getKey(), e.getMessage());
            }
        }

        log.info("Financial transformation run complete: {} symbols succeeded, {} failed, {} quarters failed to normalize", succeeded, failed, normalizeFailed);
    }
}
