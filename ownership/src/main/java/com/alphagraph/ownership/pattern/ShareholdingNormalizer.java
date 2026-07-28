package com.alphagraph.ownership.pattern;

import com.alphagraph.common.etl.Normalizer;
import com.alphagraph.ownership.api.ShareholdingPattern;
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
public class ShareholdingNormalizer implements Normalizer<RawShareholdingRow, ShareholdingPattern> {

    private final OwnershipInstrumentLookup instrumentLookup;

    public ShareholdingNormalizer(OwnershipInstrumentLookup instrumentLookup) {
        this.instrumentLookup = instrumentLookup;
    }

    @Override
    public ShareholdingPattern normalize(RawShareholdingRow raw) {
        UUID instrumentId = instrumentLookup.findIdBySymbol(raw.symbol())
            .orElseThrow(() -> new IllegalStateException("Unknown instrument: " + raw.symbol()));

        return new ShareholdingPattern(
            instrumentId, raw.symbol(), LocalDate.parse(raw.periodEnd()),
            new BigDecimal(raw.promoterPct()), new BigDecimal(raw.fiiPct()), new BigDecimal(raw.diiPct()),
            toBigDecimalOrNull(raw.mfPct()), toBigDecimalOrNull(raw.publicPct())
        );
    }

    private static BigDecimal toBigDecimalOrNull(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
