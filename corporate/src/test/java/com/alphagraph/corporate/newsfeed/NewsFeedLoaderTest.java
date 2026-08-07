package com.alphagraph.corporate.newsfeed;

import com.alphagraph.corporate.api.DocumentSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NewsFeedLoaderTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final NewsRelevanceFilter relevanceFilter = mock(NewsRelevanceFilter.class);
    private final NewsFeedLoader loader = new NewsFeedLoader(jdbcTemplate, relevanceFilter);

    private NewsArticleDocument document(String extractedText) {
        return new NewsArticleDocument(
            DocumentSource.NEWS, "https://example.com/article", "Economic Times Markets",
            "Some headline", "https://example.com/article", Instant.parse("2026-08-07T10:00:00Z"), extractedText
        );
    }

    @SuppressWarnings("unchecked")
    private AtomicReference<String> stubInsertCapturingStatus() {
        when(jdbcTemplate.queryForObject(contains("COUNT"), eq(Integer.class), any(), anyString())).thenReturn(0);

        AtomicReference<String> capturedStatus = new AtomicReference<>();
        when(jdbcTemplate.query(
            contains("INSERT INTO corporate.documents"), any(RowMapper.class),
            any(), any(), any(), any(), any(), any(), any(), any(), any()
        )).thenAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            capturedStatus.set((String) args[args.length - 1]);
            return List.of(UUID.randomUUID());
        });
        return capturedStatus;
    }

    @Test
    void relevantArticleInsertedAsProcessed() {
        when(relevanceFilter.isRelevant("matched text")).thenReturn(true);
        AtomicReference<String> capturedStatus = stubInsertCapturingStatus();

        loader.load(document("matched text"));

        assertThat(capturedStatus.get()).isEqualTo("PROCESSED");
    }

    @Test
    void nonRelevantArticleInsertedAsPendingReview() {
        when(relevanceFilter.isRelevant("unrelated text")).thenReturn(false);
        AtomicReference<String> capturedStatus = stubInsertCapturingStatus();

        loader.load(document("unrelated text"));

        assertThat(capturedStatus.get()).isEqualTo("PENDING_REVIEW");
    }

    @Test
    void recentDuplicateTitleSkipsBeforeRelevanceCheckOrInsert() {
        when(jdbcTemplate.queryForObject(contains("COUNT"), eq(Integer.class), any(), anyString())).thenReturn(1);

        loader.load(document("any text"));

        verifyNoInteractions(relevanceFilter);
        verify(jdbcTemplate, never()).query(contains("INSERT"), any(RowMapper.class), any(Object[].class));
    }
}
