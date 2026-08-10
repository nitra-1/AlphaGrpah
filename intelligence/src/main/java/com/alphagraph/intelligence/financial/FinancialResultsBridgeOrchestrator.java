package com.alphagraph.intelligence.financial;

import com.alphagraph.corporate.api.DocumentFact;
import com.alphagraph.corporate.api.FinancialFactGroup;
import com.alphagraph.corporate.knowledge.FinancialResultFactReader;
import com.alphagraph.financial.api.FinancialResult;
import com.alphagraph.financial.engine.FinancialResultReader;
import com.alphagraph.financial.results.FinancialResultsLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reads {@code corporate.knowledge.FinancialResultsExtractor}'s output via {@link
 * FinancialResultFactReader} (corporate's published facts, never corporate's internal tables),
 * maps each period's facts into a {@link FinancialResult}, and loads it via financial's own
 * {@link FinancialResultsLoader} - the same read-from-one-domain, write-via-another's-own-API
 * shape {@link com.alphagraph.intelligence.institutional.InstitutionalAnalysisOrchestrator}
 * already established.
 *
 * <p><strong>Fill-gaps-only, by explicit product decision:</strong> a period is only written when
 * {@link FinancialResultReader} shows no existing row for that {@code (instrumentId, periodEnd,
 * periodType)} - an already-populated period (whether manually researched via the Add Financial
 * Data admin form, or extracted on a prior run) is never overwritten. A PDF table misread would
 * otherwise be able to silently corrupt carefully-verified data with no review step catching it;
 * this way the worst a bad extraction can do is fail to fill a genuinely empty gap, not damage
 * something that was already correct.
 */
@Component
public class FinancialResultsBridgeOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(FinancialResultsBridgeOrchestrator.class);

    private final FinancialResultFactReader factReader;
    private final FinancialResultReader resultReader;
    private final FinancialResultsLoader loader;

    public FinancialResultsBridgeOrchestrator(
        FinancialResultFactReader factReader, FinancialResultReader resultReader, FinancialResultsLoader loader
    ) {
        this.factReader = factReader;
        this.resultReader = resultReader;
        this.loader = loader;
    }

    public void fillGapsFromExtractedFacts() {
        List<FinancialFactGroup> groups = factReader.findFinancialResultFactGroups();

        int filled = 0;
        int skippedExisting = 0;
        int skippedUnparseable = 0;
        Map<UUID, Set<PeriodKey>> existingByInstrument = new HashMap<>();

        for (FinancialFactGroup group : groups) {
            FinancialResult candidate;
            try {
                candidate = toFinancialResult(group);
            } catch (Exception e) {
                log.warn("Could not parse extracted financial result for document {} (symbol={}): {}",
                    group.documentId(), group.symbol(), e.getMessage());
                skippedUnparseable++;
                continue;
            }

            Set<PeriodKey> existing = existingByInstrument.computeIfAbsent(
                group.instrumentId(),
                id -> resultReader.findPeriods(id).stream().map(r -> new PeriodKey(r.periodEnd(), r.periodType())).collect(Collectors.toSet())
            );
            PeriodKey key = new PeriodKey(candidate.periodEnd(), candidate.periodType());
            if (existing.contains(key)) {
                skippedExisting++;
                continue;
            }

            loader.load(candidate);
            existing.add(key);
            filled++;
        }

        log.info(
            "Financial results bridge: {} period(s) filled, {} skipped (already had verified data), {} skipped (unparseable)",
            filled, skippedExisting, skippedUnparseable
        );
    }

    private static FinancialResult toFinancialResult(FinancialFactGroup group) {
        Map<String, String> byType = new HashMap<>();
        for (DocumentFact fact : group.facts()) {
            byType.put(fact.factType(), fact.factValue());
        }

        LocalDate periodEnd = LocalDate.parse(require(byType, "periodend"));
        String periodType = require(byType, "periodtype");
        BigDecimal sales = new BigDecimal(require(byType, "sales"));
        BigDecimal pat = new BigDecimal(require(byType, "pat"));

        return new FinancialResult(
            group.instrumentId(), group.symbol(), periodEnd, periodType, sales, pat,
            optionalDecimal(byType, "eps"), optionalDecimal(byType, "roepercentage"), optionalDecimal(byType, "rocepercentage"),
            optionalDecimal(byType, "operatingmarginpercentage"), optionalDecimal(byType, "netmarginpercentage"),
            optionalDecimal(byType, "cashflowfromoperations"), optionalDecimal(byType, "totalassets"),
            optionalDecimal(byType, "currentassets"), optionalDecimal(byType, "currentliabilities"),
            optionalDecimal(byType, "totaldebt"), optionalDecimal(byType, "totalequity"),
            optionalDecimal(byType, "interestexpense"), optionalDecimal(byType, "ebit")
        );
    }

    private static String require(Map<String, String> byType, String key) {
        String value = byType.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required fact '" + key + "'");
        }
        return value;
    }

    private static BigDecimal optionalDecimal(Map<String, String> byType, String key) {
        String value = byType.get(key);
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }

    private record PeriodKey(LocalDate periodEnd, String periodType) {
    }
}
