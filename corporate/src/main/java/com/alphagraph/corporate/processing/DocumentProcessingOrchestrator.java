package com.alphagraph.corporate.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Runs the Document Pipeline's OCR/Parse -> Extract -> Chunk -> Entity Extraction -> Embeddings
 * stages over every document {@link PendingDocumentReader} finds in DOWNLOADED status, via
 * {@link NlpSidecarClient}. Entirely within the corporate module - no intelligence-bridging
 * needed, since corporate already owns both the documents and the sidecar call
 * (docs/001_System_Architecture.md §5), matching Module 1.6's Fundamental Engine precedent for
 * "self-contained, no cross-domain read required".
 *
 * A document that comes back {@code needsOcr=true} (near-empty text extraction - a scanned/image
 * PDF, and the sidecar has no OCR fallback yet) is marked NEEDS_OCR and left there rather than
 * retried indefinitely; nothing currently re-attempts NEEDS_OCR documents once Tesseract-based OCR
 * is added to the sidecar - a real, disclosed gap, not a silent one.
 */
@Component
public class DocumentProcessingOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingOrchestrator.class);

    private final PendingDocumentReader pendingDocumentReader;
    private final NlpSidecarClient sidecarClient;
    private final DocumentChunkWriter chunkWriter;
    private final DocumentEntityWriter entityWriter;
    private final JdbcTemplate jdbcTemplate;

    public DocumentProcessingOrchestrator(
        PendingDocumentReader pendingDocumentReader, NlpSidecarClient sidecarClient,
        DocumentChunkWriter chunkWriter, DocumentEntityWriter entityWriter, JdbcTemplate jdbcTemplate
    ) {
        this.pendingDocumentReader = pendingDocumentReader;
        this.sidecarClient = sidecarClient;
        this.chunkWriter = chunkWriter;
        this.entityWriter = entityWriter;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void processAllPending() {
        List<PendingDocument> pending = pendingDocumentReader.findDownloaded();

        int processed = 0;
        int needsOcr = 0;
        int failed = 0;
        for (PendingDocument document : pending) {
            try {
                if (processOne(document)) {
                    processed++;
                } else {
                    needsOcr++;
                }
            } catch (Exception e) {
                log.error("Failed to process document {} (symbol={}): {}", document.id(), document.symbol(), e.getMessage(), e);
                failed++;
            }
        }

        log.info("Document processing: {} processed, {} need OCR, {} failed (of {} pending)",
            processed, needsOcr, failed, pending.size());
    }

    /** Returns true if the document was fully processed, false if it was marked NEEDS_OCR instead. */
    private boolean processOne(PendingDocument document) {
        String filename = document.symbol() + "_" + document.externalId() + ".pdf";
        SidecarProcessedDocumentResponse result = sidecarClient.process(document.rawPdf(), filename);

        if (result.needsOcr()) {
            jdbcTemplate.update(
                "UPDATE corporate.documents SET status = 'NEEDS_OCR' WHERE id = ?", document.id()
            );
            return false;
        }

        for (SidecarChunkResponse chunk : result.chunks()) {
            UUID chunkId = chunkWriter.write(document.id(), chunk.index(), chunk.text(), chunk.wordCount(), chunk.embedding());
            for (SidecarEntityResponse entity : chunk.entities()) {
                entityWriter.write(document.id(), chunkId, entity.text(), entity.label());
            }
        }

        jdbcTemplate.update(
            "UPDATE corporate.documents SET extracted_text = ?, status = 'PROCESSED' WHERE id = ?",
            result.fullText(), document.id()
        );
        return true;
    }
}
