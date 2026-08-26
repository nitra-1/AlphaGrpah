package com.alphagraph.ownership.deals;

import com.alphagraph.common.etl.Normalizer;
import com.alphagraph.ownership.api.BulkDeal;
import com.alphagraph.ownership.pattern.OwnershipInstrumentLookup;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * An unresolvable symbol throws rather than returning a partial record — Pipeline's per-record
 * try/catch quarantines it as a rejected row instead of failing the whole run, per
 * docs/002_Engine_Architecture.md §2. Reuses {@link OwnershipInstrumentLookup} from the sibling
 * {@code ownership.pattern} package rather than duplicating it - that duplication convention is
 * about not depending on another domain MODULE, not about avoiding reuse within the same one.
 *
 * <p>Before throwing on an unresolved symbol, hands the raw row to {@link DiscoveredDealWriter} -
 * real institutional buying in a stock AlphaGraph doesn't track yet is a genuine discovery signal,
 * not noise to be silently dropped. See the admin Discovery review page.
 */
@Component
public class BulkDealsNormalizer implements Normalizer<RawDealRow, BulkDeal> {

    // Real NSE bulk/block deals dates are all-caps ("28-JUL-2026"), unlike bhavdata's title-case
    // ("28-Jul-2026") - case-insensitive parsing avoids depending on that formatting detail.
    // Package-private, not private: DiscoveredDealWriter parses the same raw date format.
    static final DateTimeFormatter DATE_FORMAT = new DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern("dd-MMM-yyyy")
        .toFormatter(Locale.ENGLISH);

    private final OwnershipInstrumentLookup instrumentLookup;
    private final DiscoveredDealWriter discoveredDealWriter;

    public BulkDealsNormalizer(OwnershipInstrumentLookup instrumentLookup, DiscoveredDealWriter discoveredDealWriter) {
        this.instrumentLookup = instrumentLookup;
        this.discoveredDealWriter = discoveredDealWriter;
    }

    @Override
    public BulkDeal normalize(RawDealRow raw) {
        Optional<UUID> instrumentId = instrumentLookup.findIdBySymbol(raw.symbol());
        if (instrumentId.isEmpty()) {
            discoveredDealWriter.capture(raw);
            throw new IllegalStateException("Unknown instrument: " + raw.symbol());
        }

        return new BulkDeal(
            instrumentId.get(), raw.symbol(), LocalDate.parse(raw.dealDate(), DATE_FORMAT), raw.clientName(),
            raw.buySell(), Long.parseLong(raw.quantity()), new BigDecimal(raw.price()), raw.dealType()
        );
    }
}
