package com.alphagraph.corporate.processing;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentProcessingOrchestratorTest {

    private final PendingDocumentReader documentReader = mock(PendingDocumentReader.class);
    private final NlpSidecarClient sidecarClient = mock(NlpSidecarClient.class);
    private final DocumentChunkWriter chunkWriter = mock(DocumentChunkWriter.class);
    private final DocumentEntityWriter entityWriter = mock(DocumentEntityWriter.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final DocumentProcessingOrchestrator orchestrator =
        new DocumentProcessingOrchestrator(documentReader, sidecarClient, chunkWriter, entityWriter, jdbcTemplate);

    @Test
    void nonPdfSourceUrlIsDiscardedWithoutEverCallingTheSidecar() {
        UUID id = UUID.randomUUID();
        PendingDocument document = new PendingDocument(id, new byte[] {1, 2, 3}, "BAJFINANCE", "106738480",
            "https://nsearchives.nseindia.com/corporate/BAJFINANCE_ROID_96046_KMP_Doc.zip");
        when(documentReader.findDownloaded()).thenReturn(List.of(document));

        orchestrator.processAllPending();

        verify(sidecarClient, never()).process(any(), anyString());
        verify(jdbcTemplate).update(eq("UPDATE corporate.documents SET status = 'DISCARDED' WHERE id = ?"), eq(id));
    }

    @Test
    void nullSourceUrlIsDiscardedWithoutEverCallingTheSidecar() {
        UUID id = UUID.randomUUID();
        PendingDocument document = new PendingDocument(id, new byte[] {1, 2, 3}, "TCS", "1", null);
        when(documentReader.findDownloaded()).thenReturn(List.of(document));

        orchestrator.processAllPending();

        verify(sidecarClient, never()).process(any(), anyString());
        verify(jdbcTemplate).update(eq("UPDATE corporate.documents SET status = 'DISCARDED' WHERE id = ?"), eq(id));
    }

    @Test
    void uppercasePdfExtensionIsStillAcceptedAsAPdf() {
        UUID id = UUID.randomUUID();
        PendingDocument document = new PendingDocument(id, new byte[] {1, 2, 3}, "TCS", "1",
            "https://nsearchives.nseindia.com/corporate/TCS_Announcement.PDF");
        when(documentReader.findDownloaded()).thenReturn(List.of(document));
        when(sidecarClient.process(any(), anyString()))
            .thenReturn(new SidecarProcessedDocumentResponse(1, "full text", false, List.of()));

        orchestrator.processAllPending();

        verify(sidecarClient).process(eq(document.rawPdf()), eq("TCS_1.pdf"));
        verify(jdbcTemplate, never()).update(eq("UPDATE corporate.documents SET status = 'DISCARDED' WHERE id = ?"), any(Object[].class));
    }

    @Test
    void realPdfIsProcessedNormallyAndMarkedProcessed() {
        UUID id = UUID.randomUUID();
        PendingDocument document = new PendingDocument(id, new byte[] {1, 2, 3}, "HCLTECH", "42",
            "https://nsearchives.nseindia.com/corporate/HCLTECH_Announcement.pdf");
        when(documentReader.findDownloaded()).thenReturn(List.of(document));
        when(sidecarClient.process(any(), anyString()))
            .thenReturn(new SidecarProcessedDocumentResponse(1, "full text", false, List.of()));

        orchestrator.processAllPending();

        verify(jdbcTemplate).update(
            eq("UPDATE corporate.documents SET extracted_text = ?, status = 'PROCESSED' WHERE id = ?"),
            eq("full text"), eq(id)
        );
    }

    @Test
    void needsOcrPdfIsMarkedNeedsOcrNotDiscarded() {
        UUID id = UUID.randomUUID();
        PendingDocument document = new PendingDocument(id, new byte[] {1, 2, 3}, "HCLTECH", "42",
            "https://nsearchives.nseindia.com/corporate/HCLTECH_Announcement.pdf");
        when(documentReader.findDownloaded()).thenReturn(List.of(document));
        when(sidecarClient.process(any(), anyString()))
            .thenReturn(new SidecarProcessedDocumentResponse(1, "", true, List.of()));

        orchestrator.processAllPending();

        verify(jdbcTemplate).update(eq("UPDATE corporate.documents SET status = 'NEEDS_OCR' WHERE id = ?"), eq(id));
    }
}
