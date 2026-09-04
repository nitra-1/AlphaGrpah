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
    void resolvesKnownSymbolAndClassifiesTheSubject() {
        UUID instrumentId = UUID.randomUUID();
        when(instrumentLookup.findIdBySymbol("TCS")).thenReturn(Optional.of(instrumentId));

        RawCorporateActionRow raw = new RawCorporateActionRow(
            "TCS", "Dividend - Rs 30.00 Per Share", "04-Jun-2025", "04-Jun-2025", "10-Apr-2025"
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
            "ZOMATO", "Dividend - Rs 1.00 Per Share", "30-Jun-2025", "30-Jun-2025", null
        );

        assertThatIllegalStateException()
            .isThrownBy(() -> normalizer.normalize(raw))
            .withMessageContaining("ZOMATO");
    }

    @Test
    void unclassifiableSubjectThrowsRatherThanGuessingAnActionType() {
        when(instrumentLookup.findIdBySymbol("RELIANCE")).thenReturn(Optional.of(UUID.randomUUID()));

        RawCorporateActionRow raw = new RawCorporateActionRow(
            "RELIANCE", "Change in Registrar and Share Transfer Agent", "14-Aug-2025", null, null
        );

        assertThatIllegalStateException()
            .isThrownBy(() -> normalizer.normalize(raw))
            .withMessageContaining("Change in Registrar");
    }

    @Test
    void nullOptionalFieldsStayNull() {
        when(instrumentLookup.findIdBySymbol("RELIANCE")).thenReturn(Optional.of(UUID.randomUUID()));

        RawCorporateActionRow raw = new RawCorporateActionRow(
            "RELIANCE", "Dividend - Rs 5.50 Per Share", "14-Aug-2025", "14-Aug-2025", null
        );

        CorporateAction action = normalizer.normalize(raw);

        assertThat(action.announcementDate()).isNull();
        assertThat(action.ratioNumerator()).isNull();
        assertThat(action.ratioDenominator()).isNull();
        assertThat(action.price()).isNull();
    }

    @Test
    void bonusSubjectYieldsRatioAndNoDividendAmount() {
        when(instrumentLookup.findIdBySymbol("GOODLUCK")).thenReturn(Optional.of(UUID.randomUUID()));

        RawCorporateActionRow raw = new RawCorporateActionRow("GOODLUCK", "Bonus 2:1", "21-Aug-2026", null, null);

        CorporateAction action = normalizer.normalize(raw);

        assertThat(action.actionType()).isEqualTo("BONUS");
        assertThat(action.ratioNumerator()).isEqualTo(2);
        assertThat(action.ratioDenominator()).isEqualTo(1);
        assertThat(action.dividendAmount()).isNull();
    }
}
