package com.alphagraph.ownership.pattern;

import com.alphagraph.common.etl.Normalizer;
import com.alphagraph.ownership.api.ShareholdingPattern;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Locale;
import java.util.UUID;

/**
 * An unresolvable symbol throws rather than returning a partial record — Pipeline's per-record
 * try/catch quarantines it as a rejected row instead of failing the whole run, per
 * docs/002_Engine_Architecture.md §2.
 *
 * <p>{@code fiiPct}/{@code diiPct} are no longer guaranteed present (the live source only carries
 * promoter/public directly - FII/DII arrive later via XBRL enrichment), so they get the same
 * defensive treatment {@code mfPct}/{@code publicPct} already had. Also captures the quarter's real
 * XBRL filing URL as a best-effort side effect (see {@link ShareholdingXbrlUrlWriter}) without
 * widening {@link ShareholdingPattern} itself.
 */
@Component
public class ShareholdingNormalizer implements Normalizer<RawShareholdingRow, ShareholdingPattern> {

    // Real NSE shareholding quarter-end dates are all-caps ("30-JUN-2026"), same convention as
    // ownership.deals.BulkDealsNormalizer's own DATE_FORMAT.
    private static final DateTimeFormatter DATE_FORMAT = new DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern("dd-MMM-yyyy")
        .toFormatter(Locale.ENGLISH);

    private final OwnershipInstrumentLookup instrumentLookup;
    private final ShareholdingXbrlUrlWriter xbrlUrlWriter;

    public ShareholdingNormalizer(OwnershipInstrumentLookup instrumentLookup, ShareholdingXbrlUrlWriter xbrlUrlWriter) {
        this.instrumentLookup = instrumentLookup;
        this.xbrlUrlWriter = xbrlUrlWriter;
    }

    @Override
    public ShareholdingPattern normalize(RawShareholdingRow raw) {
        UUID instrumentId = instrumentLookup.findIdBySymbol(raw.symbol())
            .orElseThrow(() -> new IllegalStateException("Unknown instrument: " + raw.symbol()));

        LocalDate periodEnd = LocalDate.parse(raw.periodEnd(), DATE_FORMAT);
        xbrlUrlWriter.capture(instrumentId, periodEnd, raw.xbrlUrl());

        return new ShareholdingPattern(
            instrumentId, raw.symbol(), periodEnd,
            new BigDecimal(raw.promoterPct()), toBigDecimalOrNull(raw.fiiPct()), toBigDecimalOrNull(raw.diiPct()),
            toBigDecimalOrNull(raw.mfPct()), toBigDecimalOrNull(raw.publicPct())
        );
    }

    private static BigDecimal toBigDecimalOrNull(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
