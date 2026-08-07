package com.alphagraph.corporate.newsfeed;

import com.alphagraph.corporate.knowledge.KnowledgeExtractionOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsReviewServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final KnowledgeExtractionOrchestrator orchestrator = mock(KnowledgeExtractionOrchestrator.class);
    private final NewsReviewService service = new NewsReviewService(jdbcTemplate, orchestrator);

    private final UUID documentId = UUID.randomUUID();

    @Test
    void keepingAPendingReviewArticleFlipsStatusAndExtractsImmediately() {
        when(jdbcTemplate.update(contains("SET status = 'PROCESSED'"), eq(documentId))).thenReturn(1);

        boolean result = service.keep(documentId);

        assertThat(result).isTrue();
        verify(orchestrator).extractDocument(documentId);
    }

    @Test
    void keepingAnAlreadyDecidedArticleDoesNothing() {
        when(jdbcTemplate.update(contains("SET status = 'PROCESSED'"), eq(documentId))).thenReturn(0);

        boolean result = service.keep(documentId);

        assertThat(result).isFalse();
        verify(orchestrator, never()).extractDocument(documentId);
    }

    @Test
    void discardingAPendingReviewArticleMarksItTerminal() {
        when(jdbcTemplate.update(contains("SET status = 'DISCARDED'"), eq(documentId))).thenReturn(1);

        boolean result = service.discard(documentId);

        assertThat(result).isTrue();
    }

    @Test
    void discardingAnAlreadyDecidedArticleReturnsFalse() {
        when(jdbcTemplate.update(contains("SET status = 'DISCARDED'"), eq(documentId))).thenReturn(0);

        boolean result = service.discard(documentId);

        assertThat(result).isFalse();
    }
}
