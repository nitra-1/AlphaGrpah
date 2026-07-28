package com.alphagraph.corporate.actions;

import com.alphagraph.corporate.api.CorporateAction;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CorporateActionsNormalizerTest {

    private final CorporateInstrumentLookup instrumentLookup = mock(CorporateInstrumentLookup.class);
    private final CorporateActionsNormalizer normalizer = new CorporateActionsNormalizer(instrumentLookup);

    @Test
    void resolvesKnownSymbolAndParsesAllFields() {
        UUID instrumentId = UUID.randomUUID();
        when(instrumentLookup.findIdBySymbol("TCS")).thenReturn(Optional.of(instrumentId));

        RawCorporateActionRow raw = new RawCorporateActionRow(
            "TCS", "DIVIDEND", "2025-04-10", "2025-06-04", "2025-06-04", "30.00", null, null, null
        );

        CorporateAction action = normalizer.normalize(raw);

        assertThat(action.instrumentId()).isEqualTo(instrumentId);
        assertThat(action.symbol()).isEqualTo("TCS");
        assertThat(action.actionType()).isEqualTo("DIVIDEND");
        assertThat(action.exDate()).isEqualTo(LocalDate.of(2025, 6, 4));
        assertThat(action.recordDate()).isEqualTo(LocalDate.of(2025, 6, 4));
        assertThat(action.announcementDate()).isEqualTo(LocalDate.of(2025, 4, 10));
        assertThat(action.dividendAmount()).isEqualByComparingTo("30.00");
        assertThat(action.ratioNumerator()).isNull();
        assertThat(action.price()).isNull();
    }

    @Test
    void unknownSymbolThrowsRatherThanReturningAPartialRecord() {
        when(instrumentLookup.findIdBySymbol("ZOMATO")).thenReturn(Optional.empty());

        RawCorporateActionRow raw = new RawCorporateActionRow(
            "ZOMATO", "DIVIDEND", null, "2025-06-30", "2025-06-30", "1.00", null, null, null
        );

        assertThatIllegalStateException()
            .isThrownBy(() -> normalizer.normalize(raw))
            .withMessageContaining("ZOMATO");
    }

    @Test
    void nullOptionalFieldsStayNull() {
        when(instrumentLookup.findIdBySymbol("RELIANCE")).thenReturn(Optional.of(UUID.randomUUID()));

        RawCorporateActionRow raw = new RawCorporateActionRow(
            "RELIANCE", "DIVIDEND", null, "2025-08-14", "2025-08-14", "5.50", null, null, null
        );

        CorporateAction action = normalizer.normalize(raw);

        assertThat(action.announcementDate()).isNull();
        assertThat(action.ratioNumerator()).isNull();
        assertThat(action.ratioDenominator()).isNull();
        assertThat(action.price()).isNull();
    }
}
