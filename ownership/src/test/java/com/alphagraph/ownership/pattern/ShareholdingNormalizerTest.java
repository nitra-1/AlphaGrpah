package com.alphagraph.ownership.pattern;

import com.alphagraph.ownership.api.ShareholdingPattern;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShareholdingNormalizerTest {

    private final OwnershipInstrumentLookup instrumentLookup = mock(OwnershipInstrumentLookup.class);
    private final ShareholdingNormalizer normalizer = new ShareholdingNormalizer(instrumentLookup);

    @Test
    void resolvesKnownSymbolAndParsesAllFields() {
        UUID instrumentId = UUID.randomUUID();
        when(instrumentLookup.findIdBySymbol("TCS")).thenReturn(Optional.of(instrumentId));

        RawShareholdingRow raw = new RawShareholdingRow("TCS", "2026-06-30", "71.77", "9.66", "13.41", null, "5.16");

        ShareholdingPattern pattern = normalizer.normalize(raw);

        assertThat(pattern.instrumentId()).isEqualTo(instrumentId);
        assertThat(pattern.symbol()).isEqualTo("TCS");
        assertThat(pattern.periodEnd()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(pattern.promoterPercentage()).isEqualByComparingTo("71.77");
        assertThat(pattern.fiiPercentage()).isEqualByComparingTo("9.66");
        assertThat(pattern.diiPercentage()).isEqualByComparingTo("13.41");
        assertThat(pattern.mfPercentage()).isNull();
        assertThat(pattern.publicPercentage()).isEqualByComparingTo("5.16");
    }

    @Test
    void unknownSymbolThrowsRatherThanReturningAPartialRecord() {
        when(instrumentLookup.findIdBySymbol("ZOMATO")).thenReturn(Optional.empty());

        RawShareholdingRow raw = new RawShareholdingRow("ZOMATO", "2026-06-30", "0.00", "45.00", "15.00", null, "40.00");

        assertThatIllegalStateException()
            .isThrownBy(() -> normalizer.normalize(raw))
            .withMessageContaining("ZOMATO");
    }

    @Test
    void nullMfPercentageStaysNull() {
        when(instrumentLookup.findIdBySymbol("INFY")).thenReturn(Optional.of(UUID.randomUUID()));

        RawShareholdingRow raw = new RawShareholdingRow("INFY", "2025-12-31", "13.30", "36.30", "37.90", null, "12.60");

        ShareholdingPattern pattern = normalizer.normalize(raw);

        assertThat(pattern.mfPercentage()).isNull();
    }
}
