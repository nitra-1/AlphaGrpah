package com.alphagraph.market.pricing;

import com.alphagraph.common.etl.Normalizer;
import com.alphagraph.market.api.DailyPrice;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Resolves the "Nifty 50" index row against {@code reference.instruments} under the symbol
 * {@code NIFTY50} - the same symbol {@code learning.outcomes.BenchmarkReturnCalculator} looks up
 * by default (@{@code alphagraph.learning.market-benchmark-symbol}). An unresolvable symbol throws
 * rather than returning a partial record, same as {@link BhavdataNormalizer}: NIFTY50 must be
 * added to {@code reference.instruments} before this pipeline can load anything, and until then
 * every run's row is correctly quarantined as rejected, not silently dropped.
 */
@Component
public class IndexBhavdataNormalizer implements Normalizer<RawIndexRow, DailyPrice> {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final String INSTRUMENT_SYMBOL = "NIFTY50";

    private final InstrumentLookup instrumentLookup;

    public IndexBhavdataNormalizer(InstrumentLookup instrumentLookup) {
        this.instrumentLookup = instrumentLookup;
    }

    @Override
    public DailyPrice normalize(RawIndexRow raw) {
        UUID instrumentId = instrumentLookup.findIdBySymbol(INSTRUMENT_SYMBOL)
            .orElseThrow(() -> new IllegalStateException("Unknown instrument: " + INSTRUMENT_SYMBOL));

        return new DailyPrice(
            instrumentId, INSTRUMENT_SYMBOL, LocalDate.parse(raw.tradeDate(), DATE_FORMAT),
            new BigDecimal(raw.open()), new BigDecimal(raw.high()), new BigDecimal(raw.low()), new BigDecimal(raw.close()),
            Long.parseLong(raw.volume()), null
        );
    }
}
