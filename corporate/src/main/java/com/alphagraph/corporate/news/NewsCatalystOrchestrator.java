package com.alphagraph.corporate.news;

import com.alphagraph.corporate.api.NewsCatalystSnapshot;
import com.alphagraph.corporate.api.NewsInstrumentLink;
import com.alphagraph.corporate.knowledge.DocumentConsumerCheckpointWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Runs the News Catalyst Engine's two phases, mirroring {@code corporate.commentary.
 * ManagementCommentaryOrchestrator} exactly. First, parses every un-checkpointed
 * KNOWLEDGE_EXTRACTED document's canonical facts into {@code document_instrument_links} rows
 * (Layer 1) - a document naming no tracked companies, or none at all, simply adds no rows.
 * Second, re-aggregates every instrument with any link history into a fresh
 * {@link NewsCatalystSnapshot} (Layer 2) via {@link NewsCatalystEngine}.
 */
@Component
public class NewsCatalystOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(NewsCatalystOrchestrator.class);

    private final PendingNewsDocumentReader documentReader;
    private final NewsLinkParser parser;
    private final NewsLinkWriter linkWriter;
    private final DocumentConsumerCheckpointWriter checkpointWriter;
    private final NewsLinkReader linkReader;
    private final NewsCatalystRuleSetLoader ruleSetLoader;
    private final NewsCatalystEngine engine;
    private final NewsCatalystSnapshotStore snapshotStore;

    public NewsCatalystOrchestrator(
        PendingNewsDocumentReader documentReader, NewsLinkParser parser, NewsLinkWriter linkWriter,
        DocumentConsumerCheckpointWriter checkpointWriter, NewsLinkReader linkReader,
        NewsCatalystRuleSetLoader ruleSetLoader, NewsCatalystEngine engine, NewsCatalystSnapshotStore snapshotStore
    ) {
        this.documentReader = documentReader;
        this.parser = parser;
        this.linkWriter = linkWriter;
        this.checkpointWriter = checkpointWriter;
        this.linkReader = linkReader;
        this.ruleSetLoader = ruleSetLoader;
        this.engine = engine;
        this.snapshotStore = snapshotStore;
    }

    public void runNewsCatalystUpdate() {
        int newLinks = parseNewDocuments();
        int instrumentsUpdated = recomputeAllInstruments();
        log.info("News Catalyst update: {} new links, {} instrument snapshots recomputed", newLinks, instrumentsUpdated);
    }

    private int parseNewDocuments() {
        List<PendingNewsDocument> pending = documentReader.findUnprocessed();
        int newLinks = 0;
        for (PendingNewsDocument document : pending) {
            try {
                List<NewsInstrumentLink> links = parser.parse(document.id(), document.announcedAt(), document.facts());
                for (NewsInstrumentLink link : links) {
                    linkWriter.write(link);
                }
                newLinks += links.size();
                checkpointWriter.markProcessed(document.id(), NewsLinkReader.CONSUMER);
            } catch (Exception e) {
                log.error("Failed to parse news links for document {} ({}): {}", document.id(), document.title(), e.getMessage(), e);
            }
        }
        return newLinks;
    }

    private int recomputeAllInstruments() {
        List<UUID> instrumentIds = linkReader.findDistinctInstrumentIds();
        int updated = 0;
        for (UUID instrumentId : instrumentIds) {
            try {
                recomputeOne(instrumentId);
                updated++;
            } catch (Exception e) {
                log.error("Failed to recompute news catalyst score for instrument {}: {}", instrumentId, e.getMessage(), e);
            }
        }
        return updated;
    }

    private void recomputeOne(UUID instrumentId) {
        List<NewsInstrumentLink> links = linkReader.findByInstrument(instrumentId);
        if (links.isEmpty()) {
            return;
        }
        String symbol = links.get(0).symbol();
        LocalDate asOfDate = LocalDate.now();

        NewsCatalystInput input = new NewsCatalystInput(instrumentId, symbol, links, asOfDate);
        NewsCatalystSnapshot snapshot = engine.calculate(input, ruleSetLoader.loadActiveRules());
        snapshotStore.write(snapshot);
    }
}
