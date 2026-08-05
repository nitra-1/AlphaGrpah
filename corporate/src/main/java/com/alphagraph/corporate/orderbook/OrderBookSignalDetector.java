package com.alphagraph.corporate.orderbook;

import com.alphagraph.corporate.api.OrderBookEntry;
import com.alphagraph.corporate.api.OrderLifecycleStage;
import com.alphagraph.corporate.api.OrderSignal;
import com.alphagraph.corporate.api.OrderSignalType;
import com.alphagraph.corporate.relationships.EntityReader;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Detects dashboard-facing signals from one instrument's order-lifecycle ledger.
 * MARGIN_IMPROVING is deliberately not detected - see {@code OrderSignalType}'s javadoc for why.
 * REPEAT_CUSTOMER matching compares resolved {@code customerEntityId} values (Module 2.7 retrofit)
 * rather than customer name strings - since {@code corporate.relationships.EntityResolver} is the
 * only thing that ever produces a customer entity id, two ledger entries naming the same customer
 * always share the same id regardless of how each document happened to phrase the name, fixing a
 * real limitation Module 2.4 originally disclosed (case-insensitive string matching missed
 * genuinely-the-same customer named slightly differently across filings).
 */
@Component
class OrderBookSignalDetector {

    private static final double LARGE_ORDER_THRESHOLD_CRORE = 500.0;
    private static final Set<OrderLifecycleStage> NEW_ORDER_STAGES = Set.of(OrderLifecycleStage.NEW_ORDER, OrderLifecycleStage.TENDER_WIN);
    private static final Set<OrderLifecycleStage> ACTIVE_STAGES = Set.of(
        OrderLifecycleStage.NEW_ORDER, OrderLifecycleStage.TENDER_WIN, OrderLifecycleStage.EXECUTION_UPDATE
    );

    private final EntityReader entityReader;

    OrderBookSignalDetector(EntityReader entityReader) {
        this.entityReader = entityReader;
    }

    List<OrderSignal> detect(List<OrderBookEntry> entries, LocalDate asOfDate) {
        List<OrderSignal> signals = new ArrayList<>();
        Set<UUID> seenCustomerEntityIds = new HashSet<>();

        List<OrderBookEntry> sortedByTime = entries.stream()
            .sorted((a, b) -> a.detectedAt().compareTo(b.detectedAt()))
            .toList();

        for (OrderBookEntry entry : sortedByTime) {
            if (entry.lifecycleStage() == OrderLifecycleStage.CANCELLATION) {
                signals.add(signal(entry, OrderSignalType.ORDER_CANCELLATION, "Order cancelled"));
            }

            if (NEW_ORDER_STAGES.contains(entry.lifecycleStage())) {
                if (entry.orderValueCrore() != null && entry.orderValueCrore() >= LARGE_ORDER_THRESHOLD_CRORE) {
                    signals.add(signal(entry, OrderSignalType.LARGE_ORDER,
                        "Order value " + entry.orderValueCrore() + " Cr exceeds the large-order threshold"));
                }

                UUID customerEntityId = entry.customerEntityId();
                if (customerEntityId != null) {
                    if (seenCustomerEntityIds.contains(customerEntityId)) {
                        String customerName = entityReader.findCanonicalName(customerEntityId).orElse("this customer");
                        signals.add(signal(entry, OrderSignalType.REPEAT_CUSTOMER, "Repeat business from " + customerName));
                    }
                    seenCustomerEntityIds.add(customerEntityId);
                }
            }

            if (ACTIVE_STAGES.contains(entry.lifecycleStage())) {
                Integer endYear = parseYear(entry.executionEnd());
                if (endYear != null && endYear < asOfDate.getYear()) {
                    signals.add(signal(entry, OrderSignalType.EXECUTION_DELAY,
                        "Execution end year " + endYear + " has passed with no recorded completion"));
                }
            }
        }

        return signals;
    }

    private static OrderSignal signal(OrderBookEntry entry, OrderSignalType type, String detail) {
        return new OrderSignal(entry.instrumentId(), entry.symbol(), type, entry.id(), detail, java.time.Instant.now());
    }

    private static Integer parseYear(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
