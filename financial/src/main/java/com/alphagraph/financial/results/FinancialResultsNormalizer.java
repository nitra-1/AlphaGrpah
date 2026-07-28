package com.alphagraph.financial.results;

import com.alphagraph.common.etl.Normalizer;
import com.alphagraph.financial.api.FinancialResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An unresolvable symbol throws rather than returning a partial record — Pipeline's per-record
 * try/catch quarantines it as a rejected row instead of failing the whole run, per
 * docs/002_Engine_Architecture.md §2.
 */
@Component
public class FinancialResultsNormalizer implements Normalizer<RawFinancialResultRow, FinancialResult> {

    private final FinancialInstrumentLookup instrumentLookup;

    public FinancialResultsNormalizer(FinancialInstrumentLookup instrumentLookup) {
        this.instrumentLookup = instrumentLookup;
    }

    @Override
    public FinancialResult normalize(RawFinancialResultRow raw) {
        UUID instrumentId = instrumentLookup.findIdBySymbol(raw.symbol())
            .orElseThrow(() -> new IllegalStateException("Unknown instrument: " + raw.symbol()));

        return new FinancialResult(
            instrumentId, raw.symbol(), LocalDate.parse(raw.periodEnd()), raw.periodType(),
            new BigDecimal(raw.sales()), new BigDecimal(raw.pat()), toBigDecimalOrNull(raw.eps()),
            toBigDecimalOrNull(raw.roePct()), toBigDecimalOrNull(raw.rocePct()),
            toBigDecimalOrNull(raw.operatingMarginPct()), toBigDecimalOrNull(raw.netMarginPct()),
            toBigDecimalOrNull(raw.cashFlowFromOps())
        );
    }

    private static BigDecimal toBigDecimalOrNull(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
