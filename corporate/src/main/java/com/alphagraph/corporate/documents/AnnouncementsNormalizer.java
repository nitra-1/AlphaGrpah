package com.alphagraph.corporate.documents;

import com.alphagraph.common.etl.Normalizer;
import com.alphagraph.corporate.actions.CorporateInstrumentLookup;
import com.alphagraph.corporate.api.CorporateDocument;
import com.alphagraph.corporate.api.DocumentSource;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Locale;
import java.util.UUID;

/**
 * An unresolvable symbol throws rather than returning a partial record - Pipeline's per-record
 * try/catch quarantines it as a rejected row, same convention as every prior module's Normalizer
 * (e.g. {@code ownership.deals.BulkDealsNormalizer}, Module 1.7).
 */
@Component
public class AnnouncementsNormalizer implements Normalizer<RawAnnouncementRow, CorporateDocument> {

    // Real NSE announcement timestamps are title-case ("25-Jul-2026 14:37:56"), matching
    // bhavdata's date convention rather than bulk/block deals' all-caps one.
    private static final DateTimeFormatter TIMESTAMP_FORMAT = new DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern("dd-MMM-yyyy HH:mm:ss")
        .toFormatter(Locale.ENGLISH);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final CorporateInstrumentLookup instrumentLookup;

    public AnnouncementsNormalizer(CorporateInstrumentLookup instrumentLookup) {
        this.instrumentLookup = instrumentLookup;
    }

    @Override
    public CorporateDocument normalize(RawAnnouncementRow raw) {
        UUID instrumentId = instrumentLookup.findIdBySymbol(raw.symbol())
            .orElseThrow(() -> new IllegalStateException("Unknown instrument: " + raw.symbol()));

        Instant announcedAt = LocalDateTime.parse(raw.announcedAt(), TIMESTAMP_FORMAT).atZone(IST).toInstant();

        return new CorporateDocument(
            instrumentId, raw.symbol(), DocumentSource.EXCHANGE_ANNOUNCEMENT, raw.externalId(),
            raw.category(), raw.title(), raw.pdfUrl(), announcedAt
        );
    }
}
