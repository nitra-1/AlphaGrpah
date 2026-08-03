package com.alphagraph.corporate.orderbook;

import com.alphagraph.corporate.api.OrderBookEntry;
import com.alphagraph.corporate.api.OrderBookSnapshot;
import com.alphagraph.corporate.api.OrderSignal;
import com.alphagraph.corporate.knowledge.DocumentConsumerCheckpointWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs the Order Book Engine's two phases. First, parses every un-checkpointed
 * KNOWLEDGE_EXTRACTED document's canonical facts into {@code order_book_ledger} rows (a document
 * with no {@code orderlifecyclestage} fact simply adds no row - most documents aren't
 * order-related). Second, re-aggregates every instrument with any ledger history into a fresh
 * {@link OrderBookSnapshot} and signal set via {@link OrderBookAggregationEngine} - the whole
 * ledger, not just today's new entries, since the ledger is a running account whose totals depend
 * on everything recorded so far.
 */
@Component
public class OrderBookOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OrderBookOrchestrator.class);

    private final PendingOrderDocumentReader documentReader;
    private final OrderBookLedgerParser parser;
    private final OrderBookLedgerWriter ledgerWriter;
    private final DocumentConsumerCheckpointWriter checkpointWriter;
    private final OrderBookLedgerReader ledgerReader;
    private final OrderBookRuleSetLoader ruleSetLoader;
    private final OrderBookAggregationEngine aggregationEngine;
    private final OrderBookSnapshotStore snapshotStore;
    private final OrderBookSignalDetector signalDetector;
    private final OrderBookSignalWriter signalWriter;

    public OrderBookOrchestrator(
        PendingOrderDocumentReader documentReader, OrderBookLedgerParser parser, OrderBookLedgerWriter ledgerWriter,
        DocumentConsumerCheckpointWriter checkpointWriter, OrderBookLedgerReader ledgerReader,
        OrderBookRuleSetLoader ruleSetLoader, OrderBookAggregationEngine aggregationEngine,
        OrderBookSnapshotStore snapshotStore, OrderBookSignalDetector signalDetector, OrderBookSignalWriter signalWriter
    ) {
        this.documentReader = documentReader;
        this.parser = parser;
        this.ledgerWriter = ledgerWriter;
        this.checkpointWriter = checkpointWriter;
        this.ledgerReader = ledgerReader;
        this.ruleSetLoader = ruleSetLoader;
        this.aggregationEngine = aggregationEngine;
        this.snapshotStore = snapshotStore;
        this.signalDetector = signalDetector;
        this.signalWriter = signalWriter;
    }

    public void runOrderBookUpdate() {
        int newLedgerEntries = parseNewDocuments();
        int instrumentsUpdated = recomputeAllInstruments();
        log.info("Order Book update: {} new ledger entries, {} instrument snapshots recomputed", newLedgerEntries, instrumentsUpdated);
    }

    private int parseNewDocuments() {
        List<PendingOrderDocument> pending = documentReader.findUnprocessed();
        int newEntries = 0;
        for (PendingOrderDocument document : pending) {
            try {
                Optional<OrderBookEntry> entry = parser.parse(document.id(), document.instrumentId(), document.symbol(), document.facts());
                if (entry.isPresent()) {
                    ledgerWriter.write(entry.get());
                    newEntries++;
                }
                checkpointWriter.markProcessed(document.id(), OrderBookLedgerReader.CONSUMER);
            } catch (Exception e) {
                log.error("Failed to parse order facts for document {} (symbol={}): {}",
                    document.id(), document.symbol(), e.getMessage(), e);
            }
        }
        return newEntries;
    }

    private int recomputeAllInstruments() {
        List<UUID> instrumentIds = ledgerReader.findDistinctInstrumentIds();
        int updated = 0;
        for (UUID instrumentId : instrumentIds) {
            try {
                recomputeOne(instrumentId);
                updated++;
            } catch (Exception e) {
                log.error("Failed to recompute order book for instrument {}: {}", instrumentId, e.getMessage(), e);
            }
        }
        return updated;
    }

    private void recomputeOne(UUID instrumentId) {
        List<OrderBookEntry> entries = ledgerReader.findByInstrument(instrumentId);
        if (entries.isEmpty()) {
            return;
        }
        String symbol = entries.get(0).symbol();
        LocalDate asOfDate = LocalDate.now();

        Double previousValue = snapshotStore.findMostRecentOrderBookValue(instrumentId, asOfDate).orElse(null);
        OrderBookAggregationInput input = new OrderBookAggregationInput(instrumentId, symbol, entries, previousValue, asOfDate);
        OrderBookSnapshot snapshot = aggregationEngine.calculate(input, ruleSetLoader.loadActiveRules());
        snapshotStore.write(snapshot);

        List<OrderSignal> signals = signalDetector.detect(entries, asOfDate);
        signalWriter.replaceAll(instrumentId, signals);
    }
}
