package com.alphagraph.corporate.news;

import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.corporate.knowledge.DocumentConsumerCheckpointWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs the Economic Event engine over every un-checkpointed {@code KNOWLEDGE_EXTRACTED} news
 * document - parses {@code corporate.knowledge.NewsExtractor}'s extended fact output ({@link
 * EconomicEventParser}), resolves sectors/companies and writes the Economic Event ->
 * Sector Impact -> Company Exposure -> (untracked) Discovery Candidate chain ({@link
 * EconomicEventWriter}). Independent of {@link NewsCatalystOrchestrator} - both read the same
 * underlying facts, each with its own checkpoint consumer key, same "several independent rule
 * engines over one canonical fact store" pattern this codebase already established.
 */
@Component
public class EconomicEventOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(EconomicEventOrchestrator.class);

    private final PendingEconomicEventDocumentReader documentReader;
    private final EconomicEventParser parser;
    private final EconomicEventWriter writer;
    private final EconomicEventRuleSetLoader ruleSetLoader;
    private final DocumentConsumerCheckpointWriter checkpointWriter;

    public EconomicEventOrchestrator(
        PendingEconomicEventDocumentReader documentReader, EconomicEventParser parser, EconomicEventWriter writer,
        EconomicEventRuleSetLoader ruleSetLoader, DocumentConsumerCheckpointWriter checkpointWriter
    ) {
        this.documentReader = documentReader;
        this.parser = parser;
        this.writer = writer;
        this.ruleSetLoader = ruleSetLoader;
        this.checkpointWriter = checkpointWriter;
    }

    public void run() {
        RuleSet rules = ruleSetLoader.loadActiveRules();
        List<PendingEconomicEventDocument> pending = documentReader.findUnprocessed();

        int succeeded = 0;
        int failed = 0;
        for (PendingEconomicEventDocument document : pending) {
            try {
                ParsedNewsDocument parsed = parser.parse(document.id(), document.facts());
                writer.write(document.announcedAt(), parsed, rules);
                checkpointWriter.markProcessed(document.id(), PendingEconomicEventDocumentReader.CONSUMER);
                succeeded++;
            } catch (Exception e) {
                failed++;
                log.error("Failed to process economic event for document {}: {}", document.id(), e.getMessage(), e);
            }
        }

        log.info("Economic Event detection run complete: {} documents processed, {} failed", succeeded, failed);
    }
}
