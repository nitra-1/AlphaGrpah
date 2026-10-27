package com.alphagraph.corporate.knowledge;

import com.alphagraph.corporate.api.Sentiment;
import com.alphagraph.corporate.relationships.RelationshipBuilder;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KnowledgeExtractionOrchestratorTest {

    private final PendingKnowledgeDocumentReader documentReader = mock(PendingKnowledgeDocumentReader.class);
    private final DocumentIntelligenceEngine classificationEngine = mock(DocumentIntelligenceEngine.class);
    private final DocumentRouter router = mock(DocumentRouter.class);
    private final CanonicalKnowledgeWriter writer = mock(CanonicalKnowledgeWriter.class);
    private final RelationshipBuilder relationshipBuilder = mock(RelationshipBuilder.class);
    private final DocumentRepeatDetector repeatDetector = mock(DocumentRepeatDetector.class);
    private final AnnouncementTextBudget textBudget = mock(AnnouncementTextBudget.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final KnowledgeExtractionOrchestrator orchestrator = new KnowledgeExtractionOrchestrator(
        documentReader, classificationEngine, router, writer, relationshipBuilder, repeatDetector, textBudget, jdbcTemplate
    );

    private static final String TEXT_HASH = TextHash.of("text");
    private static final String STATUS_SQL = "UPDATE corporate.documents SET status = 'KNOWLEDGE_EXTRACTED', text_hash = ? WHERE id = ?";

    private static final DocumentClassification CLASSIFICATION =
        new DocumentClassification("NEWS", List.of("topic"), List.of(), "x", Sentiment.NEUTRAL, 80.0, List.of("NEWS"));

    private static PendingKnowledgeDocument document() {
        return new PendingKnowledgeDocument(UUID.randomUUID(), null, null, "text", "NEWS");
    }

    @Test
    void everyLlmCallHappensBeforeAnythingIsWritten() {
        PendingKnowledgeDocument document = document();
        when(documentReader.findProcessed()).thenReturn(List.of(document));
        when(classificationEngine.classify("text", "NEWS")).thenReturn(CLASSIFICATION);
        when(router.route(any())).thenReturn(List.of());

        orchestrator.extractAllPending();

        InOrder order = inOrder(classificationEngine, router, writer, jdbcTemplate);
        order.verify(classificationEngine).classify("text", "NEWS");
        order.verify(router).route(any());
        order.verify(writer).writeClassification(eq(document.id()), eq(CLASSIFICATION));
        order.verify(writer).writeFacts(eq(document.id()), anyList());
        order.verify(jdbcTemplate).update(eq(STATUS_SQL), eq(TEXT_HASH), eq(document.id()));
    }

    @Test
    void aDocumentDeferredByStage2LeavesNoTraceAndStaysProcessed() {
        // The bug this guards: Stage 1's rows used to be written before Stage 2 ran, so a Stage 2
        // failure left a document_summary row behind and every later sweep died on its primary key.
        when(documentReader.findProcessed()).thenReturn(List.of(document()));
        when(classificationEngine.classify("text", "NEWS")).thenReturn(CLASSIFICATION);
        when(router.route(any())).thenThrow(new GeminiUnavailableException("quota", null));

        orchestrator.extractAllPending();

        verifyNoInteractions(writer, jdbcTemplate);
        verify(relationshipBuilder, never()).build(any(), anyString(), anyList());
    }

    @Test
    void aDocumentDeferredByStage1LeavesNoTraceAndStaysProcessed() {
        when(documentReader.findProcessed()).thenReturn(List.of(document()));
        when(classificationEngine.classify("text", "NEWS")).thenThrow(new GeminiUnavailableException("quota", null));

        orchestrator.extractAllPending();

        verifyNoInteractions(router, writer, jdbcTemplate);
    }

    @Test
    void oneDeferredDocumentDoesNotStopTheRestOfTheSweep() {
        PendingKnowledgeDocument deferred = document();
        PendingKnowledgeDocument fine = document();
        when(documentReader.findProcessed()).thenReturn(List.of(deferred, fine));
        when(classificationEngine.classify("text", "NEWS"))
            .thenThrow(new GeminiUnavailableException("quota", null))
            .thenReturn(CLASSIFICATION);
        when(router.route(any())).thenReturn(List.of());

        orchestrator.extractAllPending();

        verify(writer, never()).writeClassification(eq(deferred.id()), any());
        verify(writer).writeClassification(eq(fine.id()), eq(CLASSIFICATION));
        verify(jdbcTemplate).update(eq(STATUS_SQL), eq(TEXT_HASH), eq(fine.id()));
    }

    @Test
    void anOrdinaryFailureAlsoLeavesNothingWrittenAndDoesNotStopTheSweep() {
        PendingKnowledgeDocument broken = document();
        PendingKnowledgeDocument fine = document();
        when(documentReader.findProcessed()).thenReturn(List.of(broken, fine));
        when(classificationEngine.classify("text", "NEWS"))
            .thenThrow(new IllegalStateException("could not parse"))
            .thenReturn(CLASSIFICATION);
        when(router.route(any())).thenReturn(List.of());

        orchestrator.extractAllPending();

        verify(writer, never()).writeClassification(eq(broken.id()), any());
        verify(writer).writeClassification(eq(fine.id()), eq(CLASSIFICATION));
    }

    private static PendingKnowledgeDocument announcement(int chars) {
        return new PendingKnowledgeDocument(UUID.randomUUID(), UUID.randomUUID(), "INFY", "x".repeat(chars), "EXCHANGE_ANNOUNCEMENT");
    }

    @Test
    void aRepeatOfAnAlreadyExtractedFilingIsLinkedAndNeverSentToAnLlm() {
        PendingKnowledgeDocument repeat = announcement(1000);
        UUID originalId = UUID.randomUUID();
        String hash = TextHash.of(repeat.extractedText());
        when(documentReader.findProcessed()).thenReturn(List.of(repeat));
        when(repeatDetector.findExtractedOriginal(repeat.id(), "EXCHANGE_ANNOUNCEMENT", repeat.instrumentId(), hash))
            .thenReturn(Optional.of(originalId));

        orchestrator.extractAllPending();

        verify(repeatDetector).markRepeat(repeat.id(), hash, originalId);
        verifyNoInteractions(classificationEngine, router, writer, textBudget, jdbcTemplate);
    }

    @Test
    void aDocumentWithNoExtractedOriginalIsExtractedNormallyAndItsHashIsRecorded() {
        PendingKnowledgeDocument fresh = announcement(1000);
        String hash = TextHash.of(fresh.extractedText());
        when(documentReader.findProcessed()).thenReturn(List.of(fresh));
        when(repeatDetector.findExtractedOriginal(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(textBudget.tryCharge("EXCHANGE_ANNOUNCEMENT", 1000)).thenReturn(true);
        when(classificationEngine.classify(fresh.extractedText(), "EXCHANGE_ANNOUNCEMENT")).thenReturn(CLASSIFICATION);
        when(router.route(any())).thenReturn(List.of());

        orchestrator.extractAllPending();

        verify(repeatDetector, never()).markRepeat(any(), any(), any());
        verify(jdbcTemplate).update(eq(STATUS_SQL), eq(hash), eq(fresh.id()));
    }

    @Test
    void anAnnouncementPastTheDaysTextAllowanceIsDeferredWithNoLlmCallAndStaysProcessed() {
        PendingKnowledgeDocument big = announcement(700000);
        when(documentReader.findProcessed()).thenReturn(List.of(big));
        when(repeatDetector.findExtractedOriginal(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(textBudget.tryCharge("EXCHANGE_ANNOUNCEMENT", 700000)).thenReturn(false);

        orchestrator.extractAllPending();

        verifyNoInteractions(classificationEngine, router, writer, jdbcTemplate);
    }

    @Test
    void newsDocumentsAreNeverChargedAgainstTheAnnouncementAllowance() {
        when(documentReader.findProcessed()).thenReturn(List.of(document()));
        when(repeatDetector.findExtractedOriginal(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(classificationEngine.classify("text", "NEWS")).thenReturn(CLASSIFICATION);
        when(router.route(any())).thenReturn(List.of());

        orchestrator.extractAllPending();

        verifyNoInteractions(textBudget);
    }

    @Test
    void aDeferredAnnouncementDoesNotStopLaterOnesThatFitFromRunning() {
        PendingKnowledgeDocument big = announcement(900000);
        PendingKnowledgeDocument small = announcement(2000);
        when(documentReader.findProcessed()).thenReturn(List.of(big, small));
        when(repeatDetector.findExtractedOriginal(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(textBudget.tryCharge("EXCHANGE_ANNOUNCEMENT", 900000)).thenReturn(false);
        when(textBudget.tryCharge("EXCHANGE_ANNOUNCEMENT", 2000)).thenReturn(true);
        when(classificationEngine.classify(small.extractedText(), "EXCHANGE_ANNOUNCEMENT")).thenReturn(CLASSIFICATION);
        when(router.route(any())).thenReturn(List.of());

        orchestrator.extractAllPending();

        verify(writer, never()).writeClassification(eq(big.id()), any());
        verify(writer).writeClassification(eq(small.id()), eq(CLASSIFICATION));
    }
}
