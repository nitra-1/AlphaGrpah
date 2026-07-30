package com.alphagraph.corporate.documents;

import com.alphagraph.corporate.api.CorporateDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import com.alphagraph.common.etl.Loader;

/**
 * Upserts document metadata on (source, external_id) - idempotent per docs/002_Engine_Architecture.md
 * §2 - then performs the pipeline's "Download" stage: fetches the real PDF bytes from
 * {@code sourceUrl} and stores them alongside the metadata row, advancing status PENDING ->
 * DOWNLOADED. A metadata row that already exists (re-run of the same day's feed) is left alone -
 * {@code corporate.documents} isn't re-downloaded once collected.
 *
 * A download failure propagates rather than silently marking the row FAILED - the metadata insert
 * already committed, so the row stays PENDING and a later run can retry the download; letting the
 * exception surface also means Pipeline correctly counts this record as rejected/quarantined
 * (docs/002_Engine_Architecture.md §2) rather than silently reporting success.
 */
@Component
public class AnnouncementsLoader implements Loader<CorporateDocument> {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementsLoader.class);

    private final JdbcTemplate jdbcTemplate;
    private final RestClient restClient;

    public AnnouncementsLoader(JdbcTemplate jdbcTemplate, RestClient.Builder restClientBuilder) {
        this.jdbcTemplate = jdbcTemplate;
        this.restClient = restClientBuilder
            .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; AlphaGraph/1.0)")
            .build();
    }

    @Override
    public void load(CorporateDocument document) {
        UUID documentId = insertMetadataIfAbsent(document);
        if (documentId == null) {
            log.debug("Document already collected, skipping re-download: source={} externalId={}",
                document.source(), document.externalId());
            return;
        }

        byte[] pdfBytes = downloadPdf(document.sourceUrl());
        jdbcTemplate.update(
            "UPDATE corporate.documents SET raw_pdf = ?, file_size_bytes = ?, status = 'DOWNLOADED' WHERE id = ?",
            pdfBytes, (long) pdfBytes.length, documentId
        );
    }

    /** Returns the new row's id, or null if a row for this (source, external_id) already existed. */
    private UUID insertMetadataIfAbsent(CorporateDocument document) {
        UUID id = UUID.randomUUID();
        List<UUID> inserted = jdbcTemplate.query(
            """
            INSERT INTO corporate.documents
                (id, instrument_id, symbol, source, external_id, category, title, source_url, announced_at, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')
            ON CONFLICT (source, external_id) DO NOTHING
            RETURNING id
            """,
            (rs, rowNum) -> (UUID) rs.getObject("id"),
            id, document.instrumentId(), document.symbol(), document.source().name(), document.externalId(),
            document.category(), document.title(), document.sourceUrl(), Timestamp.from(document.announcedAt())
        );
        return inserted.isEmpty() ? null : inserted.get(0);
    }

    private byte[] downloadPdf(String sourceUrl) {
        try {
            byte[] body = restClient.get().uri(sourceUrl).retrieve().body(byte[].class);
            if (body == null || body.length == 0) {
                throw new IllegalStateException("Empty PDF body from " + sourceUrl);
            }
            return body;
        } catch (RestClientException e) {
            throw new IllegalStateException("Failed to download document PDF from " + sourceUrl + ": " + e.getMessage(), e);
        }
    }
}
