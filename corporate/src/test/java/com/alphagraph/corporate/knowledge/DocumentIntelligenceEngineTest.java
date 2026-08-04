package com.alphagraph.corporate.knowledge;

import com.alphagraph.corporate.api.Sentiment;
import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;

/**
 * Tests Stage 1's parsing/normalization logic - turning Claude's classification JSON into a
 * {@link DocumentClassification}, plus prompt/schema construction. Stage 1 no longer extracts
 * business facts (that's Stage 2's job - see {@link OrderExtractorTest}), so there's no facts
 * field to test here anymore. Does not exercise the live Claude call - covered by this module's
 * live end-to-end verification.
 */
class DocumentIntelligenceEngineTest {

    private final DocumentIntelligenceEngine engine = new DocumentIntelligenceEngine(mock(AnthropicClient.class), new ObjectMapper());

    @Test
    void parsesFullClassificationFromValidJson() {
        String json = """
            {
              "documentType": "ORDER_ANNOUNCEMENT",
              "topics": ["Large Order", "Government Contract", "Defence"],
              "entities": ["BEL", "Ministry of Defence"],
              "summary": "BEL received a Rs 2,800 Cr defence order.",
              "sentiment": "POSITIVE",
              "confidence": 92,
              "recommendedExtractors": ["ORDER"]
            }
            """;

        DocumentClassification classification = engine.parseClassification(json);

        assertThat(classification.documentType()).isEqualTo("ORDER_ANNOUNCEMENT");
        assertThat(classification.topics()).containsExactly("Large Order", "Government Contract", "Defence");
        assertThat(classification.entities()).containsExactly("BEL", "Ministry of Defence");
        assertThat(classification.summary()).isEqualTo("BEL received a Rs 2,800 Cr defence order.");
        assertThat(classification.sentiment()).isEqualTo(Sentiment.POSITIVE);
        assertThat(classification.confidence()).isEqualTo(92);
        assertThat(classification.recommendedExtractors()).containsExactly("ORDER");
    }

    @Test
    void emptyTopicsEntitiesAndExtractorsAreValid() {
        String json = """
            {"documentType": "GENERAL_UPDATE", "topics": [], "entities": [], "summary": "Routine filing.",
             "sentiment": "NEUTRAL", "confidence": 60, "recommendedExtractors": []}
            """;

        DocumentClassification classification = engine.parseClassification(json);

        assertThat(classification.topics()).isEmpty();
        assertThat(classification.entities()).isEmpty();
        assertThat(classification.recommendedExtractors()).isEmpty();
    }

    @Test
    void malformedJsonThrowsIllegalStateException() {
        assertThatIllegalStateException()
            .isThrownBy(() -> engine.parseClassification("not valid json"))
            .withMessageContaining("Could not parse");
    }

    @Test
    void sentimentOutsideDeclaredEnumThrowsIllegalStateException() {
        String json = """
            {"documentType": "x", "topics": [], "entities": [], "summary": "x",
             "sentiment": "MIXED", "confidence": 50, "recommendedExtractors": []}
            """;

        assertThatIllegalStateException()
            .isThrownBy(() -> engine.parseClassification(json))
            .withMessageContaining("outside the declared schema enum");
    }

    @Test
    void outOfRangeConfidenceThrowsIllegalStateException() {
        String json = """
            {"documentType": "x", "topics": [], "entities": [], "summary": "x",
             "sentiment": "NEUTRAL", "confidence": 150, "recommendedExtractors": []}
            """;

        assertThatIllegalStateException()
            .isThrownBy(() -> engine.parseClassification(json))
            .withMessageContaining("out-of-range confidence");
    }

    @Test
    void normalizeFactTypeStripsCaseAndPunctuation() {
        assertThat(DocumentIntelligenceEngine.normalizeFactType("Order Value")).isEqualTo("ordervalue");
        assertThat(DocumentIntelligenceEngine.normalizeFactType("order_value")).isEqualTo("ordervalue");
        assertThat(DocumentIntelligenceEngine.normalizeFactType("orderValue")).isEqualTo("ordervalue");
    }

    @Test
    void promptIncludesDocumentTextAndRoutingVocabularyButNoFactExtraction() {
        String prompt = DocumentIntelligenceEngine.buildPrompt("BEL received a Rs 2,800 Cr order.");

        assertThat(prompt)
            .contains("recommendedExtractors", "ORDER", "MANAGEMENT")
            .contains("Large Order", "Government Contract", "Promoter Selling")
            .contains("BEL received a Rs 2,800 Cr order.")
            .contains("do NOT extract specific figures");
    }

    @Test
    void outputConfigBuildsWithoutThrowing() {
        OutputConfig outputConfig = DocumentIntelligenceEngine.buildOutputConfig();

        assertThat(outputConfig).isNotNull();
    }
}
