package com.alphagraph.financial.results;

import com.alphagraph.financial.api.FinancialResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinancialResultsNormalizerTest {

    private final FinancialInstrumentLookup instrumentLookup = mock(FinancialInstrumentLookup.class);
    private final FinancialResultsNormalizer normalizer = new FinancialResultsNormalizer(instrumentLookup);

    @Test
    void resolvesKnownSymbolAndParsesAllFields() {
        UUID instrumentId = UUID.randomUUID();
        when(instrumentLookup.findIdBySymbol("TCS")).thenReturn(Optional.of(instrumentId));

        RawFinancialResultRow raw = new RawFinancialResultRow(
            "TCS", "2025-03-31", "QUARTERLY", "64479.00", "12224.00", "33.79",
            "51.50", "69.80", "24.20", "19.00", null,
            "159629.00", "123000.00", "53001.00", "0.00", "94756.00", null, "15601.00"
        );

        FinancialResult result = normalizer.normalize(raw);

        assertThat(result.instrumentId()).isEqualTo(instrumentId);
        assertThat(result.symbol()).isEqualTo("TCS");
        assertThat(result.periodEnd()).isEqualTo(LocalDate.of(2025, 3, 31));
        assertThat(result.periodType()).isEqualTo("QUARTERLY");
        assertThat(result.sales()).isEqualByComparingTo("64479.00");
        assertThat(result.pat()).isEqualByComparingTo("12224.00");
        assertThat(result.eps()).isEqualByComparingTo("33.79");
        assertThat(result.roePercentage()).isEqualByComparingTo("51.50");
        assertThat(result.cashFlowFromOperations()).isNull();
        assertThat(result.totalAssets()).isEqualByComparingTo("159629.00");
        assertThat(result.totalDebt()).isEqualByComparingTo("0.00");
        assertThat(result.ebit()).isEqualByComparingTo("15601.00");
        assertThat(result.interestExpense()).isNull();
    }

    @Test
    void unknownSymbolThrowsRatherThanReturningAPartialRecord() {
        when(instrumentLookup.findIdBySymbol("ZOMATO")).thenReturn(Optional.empty());

        RawFinancialResultRow raw = new RawFinancialResultRow(
            "ZOMATO", "2025-03-31", "QUARTERLY", "5000.00", "200.00", null, null, null, null, null, null,
            null, null, null, null, null, null, null
        );

        assertThatIllegalStateException()
            .isThrownBy(() -> normalizer.normalize(raw))
            .withMessageContaining("ZOMATO");
    }

    @Test
    void nullOptionalFieldsStayNull() {
        when(instrumentLookup.findIdBySymbol("ESCORTS")).thenReturn(Optional.of(UUID.randomUUID()));

        RawFinancialResultRow raw = new RawFinancialResultRow(
            "ESCORTS", "2025-03-31", "QUARTERLY", "2445.00", "297.50", null, null, null, null, null, null,
            null, null, null, null, null, null, null
        );

        FinancialResult result = normalizer.normalize(raw);

        assertThat(result.eps()).isNull();
        assertThat(result.roePercentage()).isNull();
        assertThat(result.rocePercentage()).isNull();
        assertThat(result.operatingMarginPercentage()).isNull();
        assertThat(result.netMarginPercentage()).isNull();
        assertThat(result.cashFlowFromOperations()).isNull();
        assertThat(result.totalAssets()).isNull();
        assertThat(result.totalEquity()).isNull();
    }
}
