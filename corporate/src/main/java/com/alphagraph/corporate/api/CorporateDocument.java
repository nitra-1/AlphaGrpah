package com.alphagraph.corporate.api;

import java.time.Instant;
import java.util.UUID;

/**
 * One collected document's metadata, mirroring {@code corporate.documents}
 * (docs/003_Database_Architecture.md §3a) minus its lifecycle-only columns (status, raw_pdf,
 * extracted_text) - those are managed by {@code AnnouncementsLoader}/
 * {@code DocumentProcessingOrchestrator} internally, not carried on this ETL-stage record.
 * {@code externalId} is the source's own stable identifier (NSE's seq_id for exchange
 * announcements) - used as the natural upsert key instead of {@code sourceUrl}, since a source
 * could in principle republish the same announcement under a new file name.
 */
public record CorporateDocument(
    UUID instrumentId, String symbol, DocumentSource source, String externalId,
    String category, String title, String sourceUrl, Instant announcedAt
) {
}
