package com.alphagraph.ownership.pattern;

import com.alphagraph.ownership.api.ShareholdingPattern;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShareholdingNormalizerTest {

    private final OwnershipInstrumentLookup instrumentLookup = mock(OwnershipInstrumentLookup.class);
    private final ShareholdingXbrlUrlWriter xbrlUrlWriter = mock(ShareholdingXbrlUrlWriter.class);
    private final ShareholdingNormalizer normalizer = new ShareholdingNormalizer(instrumentLookup, xbrlUrlWriter);

    @Test
    void resolvesKnownSymbolAndParsesPromoterAndPublicButLeavesFiiDiiMfNull() {
        UUID instrumentId = UUID.randomUUID();
        when(instrumentLookup.findIdBySymbol("TCS")).thenReturn(Optional.of(instrumentId));

        RawShareholdingRow raw = new RawShareholdingRow(
            "TCS", "30-JUN-2026", "71.77", null, null, null, "5.16",
            "https://nsearchives.nseindia.com/corporate/xbrl/SHP_1.xml"
        );

        ShareholdingPattern pattern = normalizer.normalize(raw);

        assertThat(pattern.instrumentId()).isEqualTo(instrumentId);
        assertThat(pattern.symbol()).isEqualTo("TCS");
        assertThat(pattern.periodEnd()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(pattern.promoterPercentage()).isEqualByComparingTo("71.77");
        assertThat(pattern.publicPercentage()).isEqualByComparingTo("5.16");
        // The live source only carries promoter/public directly - fii/dii/mf come later via XBRL enrichment.
        assertThat(pattern.fiiPercentage()).isNull();
        assertThat(pattern.diiPercentage()).isNull();
        assertThat(pattern.mfPercentage()).isNull();
    }

    @Test
    void capturesTheXbrlUrlAsASideEffectWithoutWideningTheReturnedRecord() {
        UUID instrumentId = UUID.randomUUID();
        when(instrumentLookup.findIdBySymbol("TCS")).thenReturn(Optional.of(instrumentId));

        RawShareholdingRow raw = new RawShareholdingRow(
            "TCS", "30-JUN-2026", "71.77", null, null, null, "5.16",
            "https://nsearchives.nseindia.com/corporate/xbrl/SHP_1.xml"
        );

        normalizer.normalize(raw);

        verify(xbrlUrlWriter).capture(instrumentId, LocalDate.of(2026, 6, 30), "https://nsearchives.nseindia.com/corporate/xbrl/SHP_1.xml");
    }

    @Test
    void unknownSymbolThrowsRatherThanReturningAPartialRecord() {
        when(instrumentLookup.findIdBySymbol("ZOMATO")).thenReturn(Optional.empty());

        RawShareholdingRow raw = new RawShareholdingRow("ZOMATO", "30-JUN-2026", "0.00", null, null, null, "40.00", null);

        assertThatIllegalStateException()
            .isThrownBy(() -> normalizer.normalize(raw))
            .withMessageContaining("ZOMATO");
    }

    @Test
    void nullMfPercentageStaysNull() {
        when(instrumentLookup.findIdBySymbol("INFY")).thenReturn(Optional.of(UUID.randomUUID()));

        RawShareholdingRow raw = new RawShareholdingRow("INFY", "31-DEC-2025", "13.30", null, null, null, "12.60", null);

        ShareholdingPattern pattern = normalizer.normalize(raw);

        assertThat(pattern.mfPercentage()).isNull();
    }
}
