package com.alphagraph.corporate.commentary;

import com.alphagraph.corporate.api.ManagementCommentarySnapshot;
import com.alphagraph.corporate.api.ManagementObservation;
import com.alphagraph.corporate.knowledge.DocumentConsumerCheckpointWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Runs the Management Commentary Engine's two phases, mirroring
 * {@code corporate.orderbook.OrderBookOrchestrator} exactly. First, parses every un-checkpointed
 * KNOWLEDGE_EXTRACTED document's canonical facts into {@code management_observations} rows
 * (Layer 1) - a document with no management-commentary fact groups simply adds no rows. Second,
 * re-aggregates every instrument with any observation history into a fresh
 * {@link ManagementCommentarySnapshot} (Layer 2) via {@link ManagementCommentaryEngine}.
 */
@Component
public class ManagementCommentaryOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ManagementCommentaryOrchestrator.class);

    private final PendingManagementDocumentReader documentReader;
    private final ManagementObservationParser parser;
    private final ManagementObservationWriter observationWriter;
    private final DocumentConsumerCheckpointWriter checkpointWriter;
    private final ManagementObservationReader observationReader;
    private final ManagementRuleSetLoader ruleSetLoader;
    private final ManagementCommentaryEngine engine;
    private final ManagementSnapshotStore snapshotStore;

    public ManagementCommentaryOrchestrator(
        PendingManagementDocumentReader documentReader, ManagementObservationParser parser,
        ManagementObservationWriter observationWriter, DocumentConsumerCheckpointWriter checkpointWriter,
        ManagementObservationReader observationReader, ManagementRuleSetLoader ruleSetLoader,
        ManagementCommentaryEngine engine, ManagementSnapshotStore snapshotStore
    ) {
        this.documentReader = documentReader;
        this.parser = parser;
        this.observationWriter = observationWriter;
        this.checkpointWriter = checkpointWriter;
        this.observationReader = observationReader;
        this.ruleSetLoader = ruleSetLoader;
        this.engine = engine;
        this.snapshotStore = snapshotStore;
    }

    public void runManagementCommentaryUpdate() {
        int newObservations = parseNewDocuments();
        int instrumentsUpdated = recomputeAllInstruments();
        log.info("Management Commentary update: {} new observations, {} instrument snapshots recomputed", newObservations, instrumentsUpdated);
    }

    private int parseNewDocuments() {
        List<PendingManagementDocument> pending = documentReader.findUnprocessed();
        int newObservations = 0;
        for (PendingManagementDocument document : pending) {
            try {
                List<ManagementObservation> observations = parser.parse(
                    document.id(), document.instrumentId(), document.symbol(), document.announcedAt(), document.facts()
                );
                for (ManagementObservation observation : observations) {
                    observationWriter.write(observation);
                }
                newObservations += observations.size();
                checkpointWriter.markProcessed(document.id(), ManagementObservationReader.CONSUMER);
            } catch (Exception e) {
                log.error("Failed to parse management observations for document {} (symbol={}): {}",
                    document.id(), document.symbol(), e.getMessage(), e);
            }
        }
        return newObservations;
    }

    private int recomputeAllInstruments() {
        List<UUID> instrumentIds = observationReader.findDistinctInstrumentIds();
        int updated = 0;
        for (UUID instrumentId : instrumentIds) {
            try {
                recomputeOne(instrumentId);
                updated++;
            } catch (Exception e) {
                log.error("Failed to recompute management commentary for instrument {}: {}", instrumentId, e.getMessage(), e);
            }
        }
        return updated;
    }

    private void recomputeOne(UUID instrumentId) {
        List<ManagementObservation> observations = observationReader.findByInstrument(instrumentId);
        if (observations.isEmpty()) {
            return;
        }
        String symbol = observations.get(0).symbol();
        LocalDate asOfDate = LocalDate.now();

        ManagementCommentaryInput input = new ManagementCommentaryInput(instrumentId, symbol, observations, asOfDate);
        ManagementCommentarySnapshot snapshot = engine.calculate(input, ruleSetLoader.loadActiveRules());
        snapshotStore.write(snapshot);
    }
}
