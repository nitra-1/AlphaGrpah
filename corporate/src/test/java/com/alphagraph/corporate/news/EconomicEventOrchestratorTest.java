package com.alphagraph.corporate.news;

import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.corporate.knowledge.DocumentConsumerCheckpointWriter;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EconomicEventOrchestratorTest {

    private final PendingEconomicEventDocumentReader documentReader = mock(PendingEconomicEventDocumentReader.class);
    private final EconomicEventParser parser = mock(EconomicEventParser.class);
    private final EconomicEventWriter writer = mock(EconomicEventWriter.class);
    private final EconomicEventRuleSetLoader ruleSetLoader = mock(EconomicEventRuleSetLoader.class);
    private final DocumentConsumerCheckpointWriter checkpointWriter = mock(DocumentConsumerCheckpointWriter.class);
    private final EconomicEventOrchestrator orchestrator =
        new EconomicEventOrchestrator(documentReader, parser, writer, ruleSetLoader, checkpointWriter);

    private static final RuleSet EMPTY_RULES = new RuleSet(1, List.of());

    private PendingEconomicEventDocument pendingDocument(UUID id) {
        return new PendingEconomicEventDocument(id, Instant.parse("2026-09-25T10:00:00Z"), List.of());
    }

    @Test
    void noUnprocessedDocumentsWritesNothingAndCheckpointsNothing() {
        when(ruleSetLoader.loadActiveRules()).thenReturn(EMPTY_RULES);
        when(documentReader.findUnprocessed()).thenReturn(List.of());

        orchestrator.run();

        verify(writer, never()).write(any(), any(), any());
        verify(checkpointWriter, never()).markProcessed(any(), any());
    }

    @Test
    void everyUnprocessedDocumentIsParsedWrittenAndCheckpointed() {
        UUID id = UUID.randomUUID();
        PendingEconomicEventDocument document = pendingDocument(id);
        ParsedNewsDocument parsed = new ParsedNewsDocument(id, null, List.of(), List.of());
        when(ruleSetLoader.loadActiveRules()).thenReturn(EMPTY_RULES);
        when(documentReader.findUnprocessed()).thenReturn(List.of(document));
        when(parser.parse(id, document.facts())).thenReturn(parsed);

        orchestrator.run();

        verify(writer).write(document.announcedAt(), parsed, EMPTY_RULES);
        verify(checkpointWriter).markProcessed(id, PendingEconomicEventDocumentReader.CONSUMER);
    }

    @Test
    void oneDocumentFailingDoesNotStopOthersFromBeingProcessedOrCheckpointed() {
        UUID failingId = UUID.randomUUID();
        UUID succeedingId = UUID.randomUUID();
        PendingEconomicEventDocument failingDocument = pendingDocument(failingId);
        PendingEconomicEventDocument succeedingDocument = pendingDocument(succeedingId);
        ParsedNewsDocument succeedingParsed = new ParsedNewsDocument(succeedingId, null, List.of(), List.of());
        when(ruleSetLoader.loadActiveRules()).thenReturn(EMPTY_RULES);
        when(documentReader.findUnprocessed()).thenReturn(List.of(failingDocument, succeedingDocument));
        when(parser.parse(failingId, failingDocument.facts())).thenThrow(new RuntimeException("bad LLM output"));
        when(parser.parse(succeedingId, succeedingDocument.facts())).thenReturn(succeedingParsed);

        orchestrator.run();

        verify(checkpointWriter, never()).markProcessed(eq(failingId), any());
        verify(checkpointWriter).markProcessed(succeedingId, PendingEconomicEventDocumentReader.CONSUMER);
        verify(writer).write(succeedingDocument.announcedAt(), succeedingParsed, EMPTY_RULES);
    }

    @Test
    void aWriterFailureAlsoSkipsTheCheckpointForThatDocumentOnly() {
        UUID id = UUID.randomUUID();
        PendingEconomicEventDocument document = pendingDocument(id);
        ParsedNewsDocument parsed = new ParsedNewsDocument(id, null, List.of(), List.of());
        when(ruleSetLoader.loadActiveRules()).thenReturn(EMPTY_RULES);
        when(documentReader.findUnprocessed()).thenReturn(List.of(document));
        when(parser.parse(id, document.facts())).thenReturn(parsed);
        doThrow(new RuntimeException("db error")).when(writer).write(document.announcedAt(), parsed, EMPTY_RULES);

        orchestrator.run();

        verify(checkpointWriter, never()).markProcessed(eq(id), any());
    }

    @Test
    void ruleSetIsLoadedOnceAndReusedAcrossAllDocumentsInTheRun() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        PendingEconomicEventDocument first = pendingDocument(firstId);
        PendingEconomicEventDocument second = pendingDocument(secondId);
        when(ruleSetLoader.loadActiveRules()).thenReturn(EMPTY_RULES);
        when(documentReader.findUnprocessed()).thenReturn(List.of(first, second));
        when(parser.parse(any(), any())).thenReturn(new ParsedNewsDocument(firstId, null, List.of(), List.of()));

        orchestrator.run();

        verify(ruleSetLoader, times(1)).loadActiveRules();
    }
}
