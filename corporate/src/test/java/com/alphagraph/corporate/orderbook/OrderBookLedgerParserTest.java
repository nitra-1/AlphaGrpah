package com.alphagraph.corporate.orderbook;

import com.alphagraph.corporate.api.DocumentFact;
import com.alphagraph.corporate.api.EntityType;
import com.alphagraph.corporate.api.OrderBookEntry;
import com.alphagraph.corporate.api.OrderLifecycleStage;
import com.alphagraph.corporate.api.OrderScope;
import com.alphagraph.corporate.api.OrderSector;
import com.alphagraph.corporate.relationships.EntityResolver;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderBookLedgerParserTest {

    private final EntityResolver entityResolver = mock(EntityResolver.class);
    private final OrderBookLedgerParser parser = new OrderBookLedgerParser(entityResolver);
    private final UUID documentId = UUID.randomUUID();
    private final UUID instrumentId = UUID.randomUUID();

    @Test
    void parsesFullOrderFactsIntoAnEntry() {
        UUID customerEntityId = UUID.randomUUID();
        when(entityResolver.resolve(EntityType.CUSTOMER, "Ministry of Defence")).thenReturn(customerEntityId);
        List<DocumentFact> facts = List.of(
            fact("orderlifecyclestage", "NEW_ORDER", null),
            fact("customer", "Ministry of Defence", null),
            fact("ordervalue", "2800", "CRORE"),
            fact("businessunit", "Electronics", null),
            fact("executionstart", "2026", null),
            fact("executionend", "2029", null),
            fact("orderscope", "DOMESTIC", null),
            fact("ordersector", "GOVERNMENT", null)
        );

        Optional<OrderBookEntry> result = parser.parse(documentId, instrumentId, "BEL", facts);

        assertThat(result).isPresent();
        OrderBookEntry entry = result.get();
        assertThat(entry.lifecycleStage()).isEqualTo(OrderLifecycleStage.NEW_ORDER);
        assertThat(entry.customerEntityId()).isEqualTo(customerEntityId);
        assertThat(entry.orderValueCrore()).isEqualTo(2800.0);
        assertThat(entry.businessUnit()).isEqualTo("Electronics");
        assertThat(entry.executionStart()).isEqualTo("2026");
        assertThat(entry.executionEnd()).isEqualTo("2029");
        assertThat(entry.orderScope()).isEqualTo(OrderScope.DOMESTIC);
        assertThat(entry.orderSector()).isEqualTo(OrderSector.GOVERNMENT);
    }

    @Test
    void nonOrderDocumentWithoutLifecycleStageProducesNoEntry() {
        List<DocumentFact> facts = List.of(fact("someOtherFact", "value", null));

        assertThat(parser.parse(documentId, instrumentId, "TCS", facts)).isEmpty();
    }

    @Test
    void unrecognizedLifecycleValueProducesNoEntry() {
        List<DocumentFact> facts = List.of(fact("orderlifecyclestage", "NOT_A_REAL_STAGE", null));

        assertThat(parser.parse(documentId, instrumentId, "TCS", facts)).isEmpty();
    }

    @Test
    void convertsLakhAndAbsoluteRupeesToCrore() {
        List<DocumentFact> lakhFacts = List.of(fact("orderlifecyclestage", "NEW_ORDER", null), fact("ordervalue", "15000", "LAKH"));
        assertThat(parser.parse(documentId, instrumentId, "X", lakhFacts).get().orderValueCrore()).isEqualTo(150.0);

        List<DocumentFact> absoluteFacts = List.of(fact("orderlifecyclestage", "NEW_ORDER", null), fact("ordervalue", "10000000", ""));
        assertThat(parser.parse(documentId, instrumentId, "X", absoluteFacts).get().orderValueCrore()).isEqualTo(1.0);
    }

    @Test
    void missingOptionalFactsLeaveTheEntryFieldsNull() {
        List<DocumentFact> facts = List.of(fact("orderlifecyclestage", "COMPLETION", null));

        OrderBookEntry entry = parser.parse(documentId, instrumentId, "X", facts).get();

        assertThat(entry.customerEntityId()).isNull();
        assertThat(entry.orderValueCrore()).isNull();
        assertThat(entry.orderScope()).isNull();
    }

    @Test
    void blankCustomerFactDoesNotCallEntityResolver() {
        List<DocumentFact> facts = List.of(fact("orderlifecyclestage", "NEW_ORDER", null), fact("customer", "  ", null));

        OrderBookEntry entry = parser.parse(documentId, instrumentId, "X", facts).get();

        assertThat(entry.customerEntityId()).isNull();
        verifyNoInteractions(entityResolver);
    }

    private DocumentFact fact(String type, String value, String unit) {
        return new DocumentFact(UUID.randomUUID(), documentId, type, value, unit, 90.0, Instant.now(), null, null);
    }
}
