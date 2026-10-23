package com.alphagraph.corporate.knowledge;

import com.alphagraph.corporate.api.Sentiment;
import com.alphagraph.corporate.relationships.RelationshipBuilder;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
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
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final KnowledgeExtractionOrchestrator orchestrator = new KnowledgeExtractionOrchestrator(
        documentReader, classificationEngine, router, writer, relationshipBuilder, jdbcTemplate
    );

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
        order.verify(jdbcTemplate).update(eq("UPDATE corporate.documents SET status = 'KNOWLEDGE_EXTRACTED' WHERE id = ?"), eq(document.id()));
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
        verify(jdbcTemplate).update(eq("UPDATE corporate.documents SET status = 'KNOWLEDGE_EXTRACTED' WHERE id = ?"), eq(fine.id()));
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
}
