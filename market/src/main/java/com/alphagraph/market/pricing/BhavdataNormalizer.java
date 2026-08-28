package com.alphagraph.market.pricing;

import com.alphagraph.common.etl.Normalizer;
import com.alphagraph.market.api.DailyPrice;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * An unresolvable symbol throws rather than returning a partial record — Pipeline's per-record
 * try/catch quarantines it as a rejected row instead of failing the whole run, per
 * docs/002_Engine_Architecture.md §2.
 *
 * <p>Before throwing, checks {@link DiscoveryCandidateLookup} - if the symbol is a genuine
 * bulk/block deal Discovery candidate (Sprint 2), captures the raw row via
 * {@link DiscoveredPriceWriter} so its 20-trading-day ADTV becomes computable. Deliberately gated:
 * without this check, every one of the thousands of untracked NSE symbols in the daily bhavdata
 * file would get captured, not just the small set genuinely under Discovery review.
 */
@Component
public class BhavdataNormalizer implements Normalizer<RawDeliveryRow, DailyPrice> {

    // Package-private, not private: DiscoveredPriceWriter parses the same raw date format.
    static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    private final InstrumentLookup instrumentLookup;
    private final DiscoveryCandidateLookup discoveryCandidateLookup;
    private final DiscoveredPriceWriter discoveredPriceWriter;

    public BhavdataNormalizer(
        InstrumentLookup instrumentLookup, DiscoveryCandidateLookup discoveryCandidateLookup,
        DiscoveredPriceWriter discoveredPriceWriter
    ) {
        this.instrumentLookup = instrumentLookup;
        this.discoveryCandidateLookup = discoveryCandidateLookup;
        this.discoveredPriceWriter = discoveredPriceWriter;
    }

    @Override
    public DailyPrice normalize(RawDeliveryRow raw) {
        Optional<UUID> instrumentId = instrumentLookup.findIdBySymbol(raw.symbol());
        if (instrumentId.isEmpty()) {
            if (discoveryCandidateLookup.isCandidate(raw.symbol())) {
                discoveredPriceWriter.capture(raw);
            }
            throw new IllegalStateException("Unknown instrument: " + raw.symbol());
        }

        BigDecimal deliveryPercentage = raw.deliveryPercentage() == null || raw.deliveryPercentage().isBlank()
            ? null
            : new BigDecimal(raw.deliveryPercentage());

        return new DailyPrice(
            instrumentId.get(), raw.symbol(), LocalDate.parse(raw.tradeDate(), DATE_FORMAT),
            new BigDecimal(raw.open()), new BigDecimal(raw.high()), new BigDecimal(raw.low()), new BigDecimal(raw.close()),
            Long.parseLong(raw.volume()), deliveryPercentage
        );
    }
}
