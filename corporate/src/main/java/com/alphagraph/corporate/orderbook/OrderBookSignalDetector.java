package com.alphagraph.corporate.orderbook;

import com.alphagraph.corporate.api.OrderBookEntry;
import com.alphagraph.corporate.api.OrderLifecycleStage;
import com.alphagraph.corporate.api.OrderSignal;
import com.alphagraph.corporate.api.OrderSignalType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Detects dashboard-facing signals from one instrument's order-lifecycle ledger.
 * MARGIN_IMPROVING is deliberately not detected - see {@code OrderSignalType}'s javadoc for why.
 * REPEAT_CUSTOMER matching is a simple case-insensitive/trimmed name comparison - a real,
 * disclosed limitation (the same customer can appear under slightly different names across
 * filings), not a fuzzy-matching system, matching this project's precedent of shipping a real,
 * simple implementation over an elaborate one when the data doesn't yet demand it.
 */
@Component
class OrderBookSignalDetector {

    private static final double LARGE_ORDER_THRESHOLD_CRORE = 500.0;
    private static final Set<OrderLifecycleStage> NEW_ORDER_STAGES = Set.of(OrderLifecycleStage.NEW_ORDER, OrderLifecycleStage.TENDER_WIN);
    private static final Set<OrderLifecycleStage> ACTIVE_STAGES = Set.of(
        OrderLifecycleStage.NEW_ORDER, OrderLifecycleStage.TENDER_WIN, OrderLifecycleStage.EXECUTION_UPDATE
    );

    List<OrderSignal> detect(List<OrderBookEntry> entries, LocalDate asOfDate) {
        List<OrderSignal> signals = new ArrayList<>();
        Set<String> seenCustomers = new HashSet<>();

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

                String normalizedCustomer = normalizeCustomer(entry.customer());
                if (normalizedCustomer != null) {
                    if (seenCustomers.contains(normalizedCustomer)) {
                        signals.add(signal(entry, OrderSignalType.REPEAT_CUSTOMER, "Repeat business from " + entry.customer()));
                    }
                    seenCustomers.add(normalizedCustomer);
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

    private static String normalizeCustomer(String customer) {
        return customer == null || customer.isBlank() ? null : customer.trim().toLowerCase();
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
