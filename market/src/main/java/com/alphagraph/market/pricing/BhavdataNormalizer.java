package com.alphagraph.market.pricing;

import com.alphagraph.common.etl.Normalizer;
import com.alphagraph.market.api.DailyPrice;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * An unresolvable symbol throws rather than returning a partial record — Pipeline's per-record
 * try/catch quarantines it as a rejected row instead of failing the whole run, per
 * docs/002_Engine_Architecture.md §2.
 */
@Component
public class BhavdataNormalizer implements Normalizer<RawDeliveryRow, DailyPrice> {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    private final InstrumentLookup instrumentLookup;

    public BhavdataNormalizer(InstrumentLookup instrumentLookup) {
        this.instrumentLookup = instrumentLookup;
    }

    @Override
    public DailyPrice normalize(RawDeliveryRow raw) {
        UUID instrumentId = instrumentLookup.findIdBySymbol(raw.symbol())
            .orElseThrow(() -> new IllegalStateException("Unknown instrument: " + raw.symbol()));

        BigDecimal deliveryPercentage = raw.deliveryPercentage() == null || raw.deliveryPercentage().isBlank()
            ? null
            : new BigDecimal(raw.deliveryPercentage());

        return new DailyPrice(
            instrumentId, raw.symbol(), LocalDate.parse(raw.tradeDate(), DATE_FORMAT),
            new BigDecimal(raw.open()), new BigDecimal(raw.high()), new BigDecimal(raw.low()), new BigDecimal(raw.close()),
            Long.parseLong(raw.volume()), deliveryPercentage
        );
    }
}
