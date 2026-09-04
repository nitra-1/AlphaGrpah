package com.alphagraph.corporate.actions;

import com.alphagraph.common.etl.Normalizer;
import com.alphagraph.corporate.api.CorporateAction;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Locale;
import java.util.UUID;

/**
 * Two independent ways a row gets quarantined rather than guessed - Pipeline's per-record try/catch
 * rejects either one instead of failing the whole run, per docs/002_Engine_Architecture.md §2:
 * an unresolvable symbol (same convention as every other Normalizer in this codebase), and a
 * {@code subject} line {@link CorporateActionSubjectParser} can't confidently classify into one of
 * the five {@code action_type} values the schema allows.
 */
@Component
public class CorporateActionsNormalizer implements Normalizer<RawCorporateActionRow, CorporateAction> {

    // Real NSE corporate-action dates are title-case ("20-Aug-2026"), same convention as bhavdata
    // and corporate announcements.
    private static final DateTimeFormatter DATE_FORMAT = new DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern("dd-MMM-yyyy")
        .toFormatter(Locale.ENGLISH);

    private final CorporateInstrumentLookup instrumentLookup;

    public CorporateActionsNormalizer(CorporateInstrumentLookup instrumentLookup) {
        this.instrumentLookup = instrumentLookup;
    }

    @Override
    public CorporateAction normalize(RawCorporateActionRow raw) {
        UUID instrumentId = instrumentLookup.findIdBySymbol(raw.symbol())
            .orElseThrow(() -> new IllegalStateException("Unknown instrument: " + raw.symbol()));

        CorporateActionSubjectParser.ParsedSubject parsed = CorporateActionSubjectParser.parse(raw.subject())
            .orElseThrow(() -> new IllegalStateException("Unclassified corporate action subject: " + raw.subject()));

        return new CorporateAction(
            instrumentId, raw.symbol(), parsed.actionType(), LocalDate.parse(raw.exDate(), DATE_FORMAT),
            toLocalDateOrNull(raw.recordDate()), toLocalDateOrNull(raw.announcementDate()),
            parsed.dividendAmount(), parsed.ratioNumerator(), parsed.ratioDenominator(), parsed.price(),
            null // created_at is DB-generated (DEFAULT now()) at insert time, not known yet here
        );
    }

    private static LocalDate toLocalDateOrNull(String value) {
        return value == null ? null : LocalDate.parse(value, DATE_FORMAT);
    }
}
