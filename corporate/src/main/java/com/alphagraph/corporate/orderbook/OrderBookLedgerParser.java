package com.alphagraph.corporate.orderbook;

import com.alphagraph.corporate.api.DocumentFact;
import com.alphagraph.corporate.api.OrderBookEntry;
import com.alphagraph.corporate.api.OrderLifecycleStage;
import com.alphagraph.corporate.api.OrderRecurrence;
import com.alphagraph.corporate.api.OrderScope;
import com.alphagraph.corporate.api.OrderSector;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Turns one document's canonical facts (Module 2.2's output) into an {@link OrderBookEntry}, or
 * nothing if the document isn't order-related. A document is order-related only if it carries an
 * {@code orderlifecyclestage} fact with one of the 5 known lifecycle values - the exact vocabulary
 * {@code corporate.knowledge.DocumentIntelligenceEngine}'s prompt asks for; anything else (a
 * financial result, a management commentary document, ...) simply produces no ledger row, the same
 * "zero is a valid outcome" precedent as {@code corporate.corporate_events}.
 */
@Component
class OrderBookLedgerParser {

    Optional<OrderBookEntry> parse(UUID documentId, UUID instrumentId, String symbol, java.util.List<DocumentFact> facts) {
        Map<String, DocumentFact> byType = facts.stream()
            .collect(Collectors.toMap(DocumentFact::factType, f -> f, (a, b) -> a));

        DocumentFact lifecycleFact = byType.get("orderlifecyclestage");
        if (lifecycleFact == null) {
            return Optional.empty();
        }

        OrderLifecycleStage lifecycleStage;
        try {
            lifecycleStage = OrderLifecycleStage.valueOf(lifecycleFact.factValue().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        String customer = valueOf(byType, "customer");
        Double orderValueCrore = parseOrderValueCrore(byType.get("ordervalue"));
        String businessUnit = valueOf(byType, "businessunit");
        String executionStart = valueOf(byType, "executionstart");
        String executionEnd = valueOf(byType, "executionend");
        OrderScope orderScope = enumValueOf(byType, "orderscope", OrderScope.class);
        OrderSector orderSector = enumValueOf(byType, "ordersector", OrderSector.class);
        OrderRecurrence orderRecurrence = enumValueOf(byType, "orderrecurrence", OrderRecurrence.class);

        return Optional.of(new OrderBookEntry(
            UUID.randomUUID(), documentId, instrumentId, symbol,
            customer, orderValueCrore, businessUnit, executionStart, executionEnd,
            orderScope, orderSector, orderRecurrence, lifecycleStage, Instant.now()
        ));
    }

    private static String valueOf(Map<String, DocumentFact> byType, String factType) {
        DocumentFact fact = byType.get(factType);
        return fact == null ? null : fact.factValue();
    }

    private static <E extends Enum<E>> E enumValueOf(Map<String, DocumentFact> byType, String factType, Class<E> enumType) {
        DocumentFact fact = byType.get(factType);
        if (fact == null) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, fact.factValue().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Normalizes an orderValue fact to crore regardless of the unit the extraction returned (CRORE, LAKH, or ABSOLUTE rupees). */
    private static Double parseOrderValueCrore(DocumentFact orderValueFact) {
        if (orderValueFact == null) {
            return null;
        }
        double rawValue;
        try {
            rawValue = Double.parseDouble(orderValueFact.factValue().trim());
        } catch (NumberFormatException e) {
            return null;
        }

        String unit = orderValueFact.unit() == null ? "" : orderValueFact.unit().toLowerCase();
        if (unit.contains("crore")) {
            return rawValue;
        } else if (unit.contains("lakh")) {
            return rawValue / 100.0;
        }
        return rawValue / 1.0e7; // ABSOLUTE rupees -> crore
    }
}
