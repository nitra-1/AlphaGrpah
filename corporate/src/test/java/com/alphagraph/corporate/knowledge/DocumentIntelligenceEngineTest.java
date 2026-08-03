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
 * Tests the parsing/normalization logic that turns Claude's canonical structured-output JSON into
 * a {@link CanonicalExtraction}, plus prompt/schema construction. Does not exercise
 * {@link DocumentIntelligenceEngine#extract(String)}'s live Claude call - that is covered by this
 * module's live end-to-end verification, matching every other engine's split between
 * unit-testable pure logic and a separate real-service verification step.
 */
class DocumentIntelligenceEngineTest {

    private final DocumentIntelligenceEngine engine = new DocumentIntelligenceEngine(mock(AnthropicClient.class), new ObjectMapper());

    @Test
    void parsesFullExtractionFromValidJson() {
        String json = """
            {
              "documentType": "ORDER_ANNOUNCEMENT",
              "sentiment": "POSITIVE",
              "confidence": 92,
              "summary": "BEL received a Rs 2,800 Cr defence order.",
              "topics": ["Large Order", "Government Contract", "Defence"],
              "facts": [
                {"key": "customer", "value": "Ministry of Defence", "unit": ""},
                {"key": "Order Value", "value": "2800", "unit": "CRORE"}
              ]
            }
            """;

        CanonicalExtraction extraction = engine.parseExtraction(json);

        assertThat(extraction.documentType()).isEqualTo("ORDER_ANNOUNCEMENT");
        assertThat(extraction.sentiment()).isEqualTo(Sentiment.POSITIVE);
        assertThat(extraction.confidence()).isEqualTo(92);
        assertThat(extraction.summary()).isEqualTo("BEL received a Rs 2,800 Cr defence order.");
        assertThat(extraction.topics()).containsExactly("Large Order", "Government Contract", "Defence");
        assertThat(extraction.facts()).hasSize(2);
        assertThat(extraction.facts().get(0).factType()).isEqualTo("customer");
        // "Order Value" normalizes to "ordervalue" so downstream lookups survive key-naming variance.
        assertThat(extraction.facts().get(1).factType()).isEqualTo("ordervalue");
        assertThat(extraction.facts().get(1).unit()).isEqualTo("CRORE");
        assertThat(extraction.rawResponse()).isEqualTo(json);
    }

    @Test
    void emptyFactsAndTopicsAreValid() {
        String json = """
            {"documentType": "GENERAL_UPDATE", "sentiment": "NEUTRAL", "confidence": 60,
             "summary": "Routine filing.", "topics": [], "facts": []}
            """;

        CanonicalExtraction extraction = engine.parseExtraction(json);

        assertThat(extraction.topics()).isEmpty();
        assertThat(extraction.facts()).isEmpty();
    }

    @Test
    void malformedJsonThrowsIllegalStateException() {
        assertThatIllegalStateException()
            .isThrownBy(() -> engine.parseExtraction("not valid json"))
            .withMessageContaining("Could not parse");
    }

    @Test
    void sentimentOutsideDeclaredEnumThrowsIllegalStateException() {
        String json = """
            {"documentType": "x", "sentiment": "MIXED", "confidence": 50,
             "summary": "x", "topics": [], "facts": []}
            """;

        assertThatIllegalStateException()
            .isThrownBy(() -> engine.parseExtraction(json))
            .withMessageContaining("outside the declared schema enum");
    }

    @Test
    void outOfRangeConfidenceThrowsIllegalStateException() {
        String json = """
            {"documentType": "x", "sentiment": "NEUTRAL", "confidence": 150,
             "summary": "x", "topics": [], "facts": []}
            """;

        assertThatIllegalStateException()
            .isThrownBy(() -> engine.parseExtraction(json))
            .withMessageContaining("out-of-range confidence");
    }

    @Test
    void normalizeFactTypeStripsCaseAndPunctuation() {
        assertThat(DocumentIntelligenceEngine.normalizeFactType("Order Value")).isEqualTo("ordervalue");
        assertThat(DocumentIntelligenceEngine.normalizeFactType("order_value")).isEqualTo("ordervalue");
        assertThat(DocumentIntelligenceEngine.normalizeFactType("orderValue")).isEqualTo("ordervalue");
    }

    @Test
    void promptIncludesDocumentTextAndOrderFactVocabulary() {
        String prompt = DocumentIntelligenceEngine.buildPrompt("BEL received a Rs 2,800 Cr order.");

        assertThat(prompt)
            .contains("customer", "orderValue", "businessUnit", "executionStart", "executionEnd")
            .contains("orderLifecycleStage", "NEW_ORDER", "TENDER_WIN", "CANCELLATION", "COMPLETION")
            .contains("Large Order", "Government Contract", "Promoter Selling")
            .contains("BEL received a Rs 2,800 Cr order.");
    }

    @Test
    void outputConfigBuildsWithoutThrowing() {
        OutputConfig outputConfig = DocumentIntelligenceEngine.buildOutputConfig();

        assertThat(outputConfig).isNotNull();
    }
}
