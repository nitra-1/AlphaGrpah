package com.alphagraph.corporate.actions;

import com.alphagraph.common.etl.Normalizer;
import com.alphagraph.corporate.api.CorporateAction;
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
public class CorporateActionsNormalizer implements Normalizer<RawCorporateActionRow, CorporateAction> {

    private final CorporateInstrumentLookup instrumentLookup;

    public CorporateActionsNormalizer(CorporateInstrumentLookup instrumentLookup) {
        this.instrumentLookup = instrumentLookup;
    }

    @Override
    public CorporateAction normalize(RawCorporateActionRow raw) {
        UUID instrumentId = instrumentLookup.findIdBySymbol(raw.symbol())
            .orElseThrow(() -> new IllegalStateException("Unknown instrument: " + raw.symbol()));

        return new CorporateAction(
            instrumentId, raw.symbol(), raw.actionType(), LocalDate.parse(raw.exDate()),
            toLocalDateOrNull(raw.recordDate()), toLocalDateOrNull(raw.announcementDate()),
            toBigDecimalOrNull(raw.dividendAmount()), toIntegerOrNull(raw.ratioNumerator()),
            toIntegerOrNull(raw.ratioDenominator()), toBigDecimalOrNull(raw.price())
        );
    }

    private static LocalDate toLocalDateOrNull(String value) {
        return value == null ? null : LocalDate.parse(value);
    }

    private static BigDecimal toBigDecimalOrNull(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static Integer toIntegerOrNull(String value) {
        return value == null ? null : Integer.valueOf(value);
    }
}
