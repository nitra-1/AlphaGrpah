package com.alphagraph.corporate.orderbook;

import com.alphagraph.corporate.api.OrderBookEntry;
import com.alphagraph.corporate.api.OrderLifecycleStage;
import com.alphagraph.corporate.api.OrderSignal;
import com.alphagraph.corporate.api.OrderSignalType;
import com.alphagraph.corporate.relationships.EntityReader;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderBookSignalDetectorTest {

    private final EntityReader entityReader = mock(EntityReader.class);
    private final OrderBookSignalDetector detector = new OrderBookSignalDetector(entityReader);
    private final UUID instrumentId = UUID.randomUUID();
    private final LocalDate asOfDate = LocalDate.of(2026, 6, 1);

    @Test
    void flagsLargeOrderAboveThreshold() {
        List<OrderBookEntry> entries = List.of(entry(OrderLifecycleStage.NEW_ORDER, UUID.randomUUID(), 600.0, null, Instant.now()));

        List<OrderSignal> signals = detector.detect(entries, asOfDate);

        assertThat(signals).extracting(OrderSignal::signalType).contains(OrderSignalType.LARGE_ORDER);
    }

    @Test
    void doesNotFlagLargeOrderBelowThreshold() {
        List<OrderBookEntry> entries = List.of(entry(OrderLifecycleStage.NEW_ORDER, UUID.randomUUID(), 100.0, null, Instant.now()));

        assertThat(detector.detect(entries, asOfDate)).extracting(OrderSignal::signalType).doesNotContain(OrderSignalType.LARGE_ORDER);
    }

    @Test
    void flagsRepeatCustomerOnSecondOrderFromSameCustomerEntityId() {
        UUID customerEntityId = UUID.randomUUID();
        when(entityReader.findCanonicalName(customerEntityId)).thenReturn(Optional.of("Ministry of Defence"));
        Instant first = Instant.now();
        Instant second = first.plusSeconds(60);
        List<OrderBookEntry> entries = List.of(
            entry(OrderLifecycleStage.NEW_ORDER, customerEntityId, 100.0, null, first),
            entry(OrderLifecycleStage.TENDER_WIN, customerEntityId, 200.0, null, second)
        );

        List<OrderSignal> signals = detector.detect(entries, asOfDate);

        assertThat(signals).extracting(OrderSignal::signalType).contains(OrderSignalType.REPEAT_CUSTOMER);
        assertThat(signals).filteredOn(s -> s.signalType() == OrderSignalType.REPEAT_CUSTOMER)
            .extracting(OrderSignal::detail).containsExactly("Repeat business from Ministry of Defence");
    }

    @Test
    void firstOrderFromACustomerIsNotFlaggedAsRepeat() {
        List<OrderBookEntry> entries = List.of(entry(OrderLifecycleStage.NEW_ORDER, UUID.randomUUID(), 100.0, null, Instant.now()));

        assertThat(detector.detect(entries, asOfDate)).extracting(OrderSignal::signalType).doesNotContain(OrderSignalType.REPEAT_CUSTOMER);
    }

    @Test
    void differentCustomerEntityIdsAreNotFlaggedAsRepeat() {
        List<OrderBookEntry> entries = List.of(
            entry(OrderLifecycleStage.NEW_ORDER, UUID.randomUUID(), 100.0, null, Instant.now()),
            entry(OrderLifecycleStage.NEW_ORDER, UUID.randomUUID(), 200.0, null, Instant.now())
        );

        assertThat(detector.detect(entries, asOfDate)).extracting(OrderSignal::signalType).doesNotContain(OrderSignalType.REPEAT_CUSTOMER);
    }

    @Test
    void flagsExecutionDelayWhenEndYearHasPassed() {
        List<OrderBookEntry> entries = List.of(entry(OrderLifecycleStage.EXECUTION_UPDATE, UUID.randomUUID(), 100.0, "2024", Instant.now()));

        List<OrderSignal> signals = detector.detect(entries, asOfDate);

        assertThat(signals).extracting(OrderSignal::signalType).contains(OrderSignalType.EXECUTION_DELAY);
    }

    @Test
    void doesNotFlagExecutionDelayWhenEndYearIsInTheFuture() {
        List<OrderBookEntry> entries = List.of(entry(OrderLifecycleStage.EXECUTION_UPDATE, UUID.randomUUID(), 100.0, "2030", Instant.now()));

        assertThat(detector.detect(entries, asOfDate)).extracting(OrderSignal::signalType).doesNotContain(OrderSignalType.EXECUTION_DELAY);
    }

    @Test
    void flagsOrderCancellation() {
        List<OrderBookEntry> entries = List.of(entry(OrderLifecycleStage.CANCELLATION, UUID.randomUUID(), 100.0, null, Instant.now()));

        List<OrderSignal> signals = detector.detect(entries, asOfDate);

        assertThat(signals).extracting(OrderSignal::signalType).contains(OrderSignalType.ORDER_CANCELLATION);
    }

    private OrderBookEntry entry(OrderLifecycleStage stage, UUID customerEntityId, Double valueCrore, String executionEnd, Instant detectedAt) {
        return new OrderBookEntry(
            UUID.randomUUID(), UUID.randomUUID(), instrumentId, "TEST",
            customerEntityId, valueCrore, "Unit", "2026", executionEnd, null, null, null, stage, detectedAt
        );
    }
}
