package com.alphagraph.corporate.knowledge;

import com.alphagraph.corporate.api.Sentiment;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.Invocation;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

class CanonicalKnowledgeWriterTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final CanonicalKnowledgeWriter writer = new CanonicalKnowledgeWriter(jdbcTemplate);

    /** The SQL of every update in order, read off the real invocations rather than through a fragile varargs matcher. */
    private List<String> executedSql() {
        return mockingDetails(jdbcTemplate).getInvocations().stream()
            .map(Invocation::getRawArguments).map(args -> (String) args[0]).toList();
    }

    @Test
    void writingClassificationClearsAnEarlierAttemptFirstSoARetryCannotHitTheDuplicateKey() {
        DocumentClassification classification =
            new DocumentClassification("NEWS", List.of("a", "b"), List.of(), "x", Sentiment.NEUTRAL, 80.0, List.of());

        writer.writeClassification(UUID.randomUUID(), classification);

        List<String> sql = executedSql();
        assertThat(sql).hasSize(5);
        assertThat(sql.get(0)).startsWith("DELETE FROM corporate.document_topics");
        assertThat(sql.get(1)).startsWith("DELETE FROM corporate.document_summary");
        assertThat(sql.subList(2, 4)).allMatch(s -> s.startsWith("INSERT INTO corporate.document_topics"));
        assertThat(sql.get(4)).startsWith("INSERT INTO corporate.document_summary");
    }

    @Test
    void writingFactsClearsAnEarlierAttemptFirstSoARetryCannotDuplicateThem() {
        writer.writeFacts(UUID.randomUUID(), List.of(new ExtractedFact("a", "1", "", 90), new ExtractedFact("b", "2", "", 90)));

        List<String> sql = executedSql();
        assertThat(sql).hasSize(3);
        assertThat(sql.get(0)).startsWith("DELETE FROM corporate.document_facts");
        assertThat(sql.subList(1, 3)).allMatch(s -> s.startsWith("INSERT INTO corporate.document_facts"));
    }
}
