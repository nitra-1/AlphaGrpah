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
 * An unresolvable symbol is not a data-quality problem here - discovering real bulk/block activity
 * in a stock AlphaGraph doesn't track yet is this pipeline's entire reason for existing, not an
 * exceptional case. Hands the raw row to {@link DiscoveredDealWriter} (the admin Discovery review
 * page's real source) and returns {@link Optional#empty()} rather than throwing - {@code
 * common.etl.Pipeline}'s per-record loop counts a normal return as a successfully-processed row,
 * not a rejection, so this pipeline's own monitoring status only goes FAILED/PARTIAL for a genuine
 * problem (a malformed date/quantity/price that still throws), never for the ordinary, expected
 * case where most or all of a day's real deals are for untracked small-caps. Previously threw
 * {@code IllegalStateException} here instead, which {@code Pipeline}'s generic accept/reject
 * accounting - built for pipelines with exactly one correct destination - misread as "rejected" on
 * literally every historical run (never once a clean SUCCESS), even though every row was being
 * captured correctly the whole time. Reuses {@link OwnershipInstrumentLookup} from the sibling
 * {@code ownership.pattern} package rather than duplicating it - that duplication convention is
 * about not depending on another domain MODULE, not about avoiding reuse within the same one.
 */
@Component
public class BulkDealsNormalizer implements Normalizer<RawDealRow, Optional<BulkDeal>> {

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
    public Optional<BulkDeal> normalize(RawDealRow raw) {
        Optional<UUID> instrumentId = instrumentLookup.findIdBySymbol(raw.symbol());
        if (instrumentId.isEmpty()) {
            discoveredDealWriter.capture(raw);
            return Optional.empty();
        }

        return Optional.of(new BulkDeal(
            instrumentId.get(), raw.symbol(), LocalDate.parse(raw.dealDate(), DATE_FORMAT), raw.clientName(),
            raw.buySell(), Long.parseLong(raw.quantity()), new BigDecimal(raw.price()), raw.dealType()
        ));
    }
}
