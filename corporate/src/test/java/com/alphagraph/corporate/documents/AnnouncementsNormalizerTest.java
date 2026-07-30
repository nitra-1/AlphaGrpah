package com.alphagraph.corporate.documents;

import com.alphagraph.corporate.actions.CorporateInstrumentLookup;
import com.alphagraph.corporate.api.CorporateDocument;
import com.alphagraph.corporate.api.DocumentSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnnouncementsNormalizerTest {

    private final CorporateInstrumentLookup instrumentLookup = mock(CorporateInstrumentLookup.class);
    private final AnnouncementsNormalizer normalizer = new AnnouncementsNormalizer(instrumentLookup);

    @Test
    void resolvesKnownSymbolAndParsesIstTimestamp() {
        UUID instrumentId = UUID.randomUUID();
        when(instrumentLookup.findIdBySymbol("RELIANCE")).thenReturn(Optional.of(instrumentId));

        RawAnnouncementRow raw = new RawAnnouncementRow(
            "RELIANCE", "INE002A01018", "Updates", "Some presentation",
            "https://nsearchives.nseindia.com/corporate/file.pdf", "25-Jul-2026 14:37:56", "106710951"
        );

        CorporateDocument document = normalizer.normalize(raw);

        assertThat(document.instrumentId()).isEqualTo(instrumentId);
        assertThat(document.symbol()).isEqualTo("RELIANCE");
        assertThat(document.source()).isEqualTo(DocumentSource.EXCHANGE_ANNOUNCEMENT);
        assertThat(document.externalId()).isEqualTo("106710951");
        assertThat(document.category()).isEqualTo("Updates");
        assertThat(document.title()).isEqualTo("Some presentation");
        assertThat(document.sourceUrl()).isEqualTo("https://nsearchives.nseindia.com/corporate/file.pdf");

        Instant expected = ZonedDateTime.of(2026, 7, 25, 14, 37, 56, 0, ZoneId.of("Asia/Kolkata")).toInstant();
        assertThat(document.announcedAt()).isEqualTo(expected);
    }

    @Test
    void unknownSymbolThrowsRatherThanReturningAPartialRecord() {
        when(instrumentLookup.findIdBySymbol("WIPRO")).thenReturn(Optional.empty());

        RawAnnouncementRow raw = new RawAnnouncementRow(
            "WIPRO", "INE075A01022", "Updates", "Some update",
            "https://nsearchives.nseindia.com/corporate/file.pdf", "29-Jul-2026 18:15:32", "106715760"
        );

        assertThatIllegalStateException()
            .isThrownBy(() -> normalizer.normalize(raw))
            .withMessageContaining("WIPRO");
    }
}
