package com.alphagraph.ownership.deals;

import com.alphagraph.ownership.api.BulkDeal;
import com.alphagraph.ownership.pattern.OwnershipInstrumentLookup;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BulkDealsNormalizerTest {

    private final OwnershipInstrumentLookup instrumentLookup = mock(OwnershipInstrumentLookup.class);
    private final BulkDealsNormalizer normalizer = new BulkDealsNormalizer(instrumentLookup);

    @Test
    void resolvesKnownSymbolAndParsesAllFieldsIncludingAllCapsDate() {
        UUID instrumentId = UUID.randomUUID();
        when(instrumentLookup.findIdBySymbol("RELIANCE")).thenReturn(Optional.of(instrumentId));

        RawDealRow raw = new RawDealRow("BULK", "RELIANCE", "28-JUL-2026", "SOME FUND", "BUY", "222230", "83.00");

        BulkDeal deal = normalizer.normalize(raw);

        assertThat(deal.instrumentId()).isEqualTo(instrumentId);
        assertThat(deal.symbol()).isEqualTo("RELIANCE");
        assertThat(deal.dealDate()).isEqualTo(LocalDate.of(2026, 7, 28));
        assertThat(deal.clientName()).isEqualTo("SOME FUND");
        assertThat(deal.buySell()).isEqualTo("BUY");
        assertThat(deal.quantity()).isEqualTo(222230L);
        assertThat(deal.price()).isEqualByComparingTo("83.00");
        assertThat(deal.dealType()).isEqualTo("BULK");
    }

    @Test
    void unknownSymbolThrowsRatherThanReturningAPartialRecord() {
        when(instrumentLookup.findIdBySymbol("AASTHA")).thenReturn(Optional.empty());

        RawDealRow raw = new RawDealRow("BULK", "AASTHA", "28-JUL-2026", "D3 STOCK VISION LLP", "BUY", "222230", "83.00");

        assertThatIllegalStateException()
            .isThrownBy(() -> normalizer.normalize(raw))
            .withMessageContaining("AASTHA");
    }
}
