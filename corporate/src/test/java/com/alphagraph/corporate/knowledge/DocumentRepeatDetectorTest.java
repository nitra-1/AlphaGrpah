package com.alphagraph.corporate.knowledge;

import org.junit.jupiter.api.Test;
import org.mockito.invocation.Invocation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

class DocumentRepeatDetectorTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final DocumentRepeatDetector detector = new DocumentRepeatDetector(jdbcTemplate);

    private final UUID documentId = UUID.randomUUID();
    private final UUID instrumentId = UUID.randomUUID();

    @SuppressWarnings("unchecked")
    private void stubQuery(List<UUID> rows) {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(documentId), eq("EXCHANGE_ANNOUNCEMENT"), eq(instrumentId), eq("abc123")))
            .thenReturn(rows);
    }

    @Test
    void findsTheAlreadyExtractedOriginalWhenOneExists() {
        UUID originalId = UUID.randomUUID();
        stubQuery(List.of(originalId));

        assertThat(detector.findExtractedOriginal(documentId, "EXCHANGE_ANNOUNCEMENT", instrumentId, "abc123")).contains(originalId);
    }

    @Test
    void findsNothingWhenNoSameTextDocumentHasBeenExtracted() {
        stubQuery(List.of());

        assertThat(detector.findExtractedOriginal(documentId, "EXCHANGE_ANNOUNCEMENT", instrumentId, "abc123")).isEmpty();
    }

    @Test
    void theLookupOnlyTreatsAFullyExtractedNonRepeatAsTheOriginal() {
        stubQuery(List.of());
        detector.findExtractedOriginal(documentId, "EXCHANGE_ANNOUNCEMENT", instrumentId, "abc123");

        String sql = (String) mockingDetails(jdbcTemplate).getInvocations().stream()
            .map(Invocation::getRawArguments).findFirst().orElseThrow()[0];
        assertThat(sql).contains("status = 'KNOWLEDGE_EXTRACTED'").contains("duplicate_of_document_id IS NULL");
        // a deferred or failed twin must never cause this one to be skipped, and null instrument (news) must still match
        assertThat(sql).contains("instrument_id IS NOT DISTINCT FROM ?").contains("id <> ?");
    }

    @Test
    void markingARepeatSetsItExtractedAndLinksItToTheOriginalWithoutWritingAnyFacts() {
        UUID originalId = UUID.randomUUID();

        detector.markRepeat(documentId, "abc123", originalId);

        Object[] raw = mockingDetails(jdbcTemplate).getInvocations().stream()
            .map(Invocation::getRawArguments).findFirst().orElseThrow();
        assertThat((String) raw[0]).startsWith("UPDATE corporate.documents SET status = 'KNOWLEDGE_EXTRACTED'")
            .contains("duplicate_of_document_id = ?");
        assertThat((Object[]) raw[1]).containsExactly("abc123", originalId, documentId);
        assertThat(mockingDetails(jdbcTemplate).getInvocations()).hasSize(1);
    }
}
