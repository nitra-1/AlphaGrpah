package com.alphagraph.market.pricing;

import com.alphagraph.market.api.DailyPrice;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BhavdataNormalizerTest {

    private final InstrumentLookup instrumentLookup = mock(InstrumentLookup.class);
    private final BhavdataNormalizer normalizer = new BhavdataNormalizer(instrumentLookup);

    @Test
    void resolvesKnownSymbolAndParsesAllFields() {
        UUID instrumentId = UUID.randomUUID();
        when(instrumentLookup.findIdBySymbol("ACE")).thenReturn(Optional.of(instrumentId));

        RawDeliveryRow raw = new RawDeliveryRow(
            "ACE", "EQ", "24-Jul-2026", "975.20", "1051.00", "971.60", "1039.40", "2105588", "22.57"
        );

        DailyPrice price = normalizer.normalize(raw);

        assertThat(price.instrumentId()).isEqualTo(instrumentId);
        assertThat(price.symbol()).isEqualTo("ACE");
        assertThat(price.tradeDate()).isEqualTo(LocalDate.of(2026, 7, 24));
        assertThat(price.open()).isEqualByComparingTo("975.20");
        assertThat(price.high()).isEqualByComparingTo("1051.00");
        assertThat(price.low()).isEqualByComparingTo("971.60");
        assertThat(price.close()).isEqualByComparingTo("1039.40");
        assertThat(price.volume()).isEqualTo(2105588L);
        assertThat(price.deliveryPercentage()).isEqualByComparingTo("22.57");
    }

    @Test
    void unknownSymbolThrowsRatherThanReturningAPartialRecord() {
        when(instrumentLookup.findIdBySymbol("20MICRONS")).thenReturn(Optional.empty());

        RawDeliveryRow raw = new RawDeliveryRow(
            "20MICRONS", "EQ", "24-Jul-2026", "210.00", "210.00", "202.63", "204.00", "119240", "56.78"
        );

        assertThatIllegalStateException()
            .isThrownBy(() -> normalizer.normalize(raw))
            .withMessageContaining("20MICRONS");
    }

    @Test
    void blankDeliveryPercentageBecomesNull() {
        UUID instrumentId = UUID.randomUUID();
        when(instrumentLookup.findIdBySymbol("XYZ")).thenReturn(Optional.of(instrumentId));

        RawDeliveryRow raw = new RawDeliveryRow("XYZ", "EQ", "24-Jul-2026", "10", "11", "9", "10.5", "1000", "");

        DailyPrice price = normalizer.normalize(raw);

        assertThat(price.deliveryPercentage()).isNull();
    }
}
