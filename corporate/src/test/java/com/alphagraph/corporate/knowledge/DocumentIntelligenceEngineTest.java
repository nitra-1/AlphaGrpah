package com.alphagraph.corporate.knowledge;

import com.alphagraph.corporate.api.Sentiment;
import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests Stage 1's parsing/normalization logic - turning Claude's classification JSON into a
 * {@link DocumentClassification}, plus prompt/schema construction. Stage 1 no longer extracts
 * business facts (that's Stage 2's job - see {@link OrderExtractorTest}), so there's no facts
 * field to test here anymore. Does not exercise the live Claude call - covered by this module's
 * live end-to-end verification.
 */
class DocumentIntelligenceEngineTest {

    private final ClaudeDocumentClassificationClient claudeClient = mock(ClaudeDocumentClassificationClient.class);
    private final GeminiDocumentClassificationClient geminiClient = mock(GeminiDocumentClassificationClient.class);
    private final ClaudeFallbackBudget fallbackBudget = mock(ClaudeFallbackBudget.class);
    private final DocumentIntelligenceEngine engine = new DocumentIntelligenceEngine(claudeClient, geminiClient, fallbackBudget, new ObjectMapper(), true, 10000);

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

    @Test
    void schemaMapIsWhatBothProvidersActuallyGetAsked() {
        Map<String, Object> schemaMap = DocumentIntelligenceEngine.buildSchemaMap();

        assertThat(schemaMap.get("type")).isEqualTo("object");
        assertThat(schemaMap.get("additionalProperties")).isEqualTo(false);
        assertThat(schemaMap.get("required")).isEqualTo(List.of(
            "documentType", "topics", "entities", "summary", "sentiment", "confidence", "recommendedExtractors"
        ));
    }

    private static final String VALID_JSON = """
        {"documentType": "GENERAL_UPDATE", "topics": [], "entities": [], "summary": "Routine filing.",
         "sentiment": "NEUTRAL", "confidence": 60, "recommendedExtractors": []}
        """;

    @Test
    void newsSourceWithGeminiEnabledAndGeminiSucceedsNeverCallsClaude() {
        DocumentIntelligenceEngine engineWithGemini = new DocumentIntelligenceEngine(claudeClient, geminiClient, fallbackBudget, new ObjectMapper(), true, 10000);
        when(geminiClient.extractRawJson("doc text")).thenReturn(VALID_JSON);

        DocumentClassification classification = engineWithGemini.classify("doc text", "NEWS");

        assertThat(classification.documentType()).isEqualTo("GENERAL_UPDATE");
        org.mockito.Mockito.verify(geminiClient).extractRawJson("doc text");
        org.mockito.Mockito.verifyNoInteractions(claudeClient, fallbackBudget);
    }

    @Test
    void newsSourceGeminiFailureFallsBackToClaudeWhileTheDailyAllowanceLasts() {
        DocumentIntelligenceEngine engineWithGemini = new DocumentIntelligenceEngine(claudeClient, geminiClient, fallbackBudget, new ObjectMapper(), true, 10000);
        when(fallbackBudget.tryAcquire(DocumentIntelligenceEngine.FALLBACK_PURPOSE)).thenReturn(true);
        when(geminiClient.extractRawJson("doc text")).thenThrow(new IllegalStateException("Gemini API call failed"));
        when(claudeClient.extractRawJson("doc text")).thenReturn(VALID_JSON);

        DocumentClassification classification = engineWithGemini.classify("doc text", "NEWS");

        assertThat(classification.documentType()).isEqualTo("GENERAL_UPDATE");
        org.mockito.Mockito.verify(claudeClient).extractRawJson("doc text");
    }

    @Test
    void newsSourceGeminiFailureWithTheAllowanceUsedUpDefersAndNeverCallsClaude() {
        when(fallbackBudget.tryAcquire(DocumentIntelligenceEngine.FALLBACK_PURPOSE)).thenReturn(false);
        when(geminiClient.extractRawJson("doc text")).thenThrow(new IllegalStateException("Gemini API call failed"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> engine.classify("doc text", "NEWS"))
            .isInstanceOf(ExtractionDeferredException.class);
        org.mockito.Mockito.verifyNoInteractions(claudeClient);
    }

    @Test
    void newsSourceGeminiUnavailableDefersWithoutTouchingTheAllowanceOrClaude() {
        when(geminiClient.extractRawJson("doc text")).thenThrow(new GeminiUnavailableException("quota", null));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> engine.classify("doc text", "NEWS"))
            .isInstanceOf(GeminiUnavailableException.class);
        org.mockito.Mockito.verifyNoInteractions(claudeClient, fallbackBudget);
    }

    @Test
    void nonNewsSourceNeverTouchesGeminiEvenWithFlagOn() {
        DocumentIntelligenceEngine engineWithGemini = new DocumentIntelligenceEngine(claudeClient, geminiClient, fallbackBudget, new ObjectMapper(), true, 10000);
        when(claudeClient.extractRawJson("doc text")).thenReturn(VALID_JSON);

        engineWithGemini.classify("doc text", "EXCHANGE_ANNOUNCEMENT");

        org.mockito.Mockito.verifyNoInteractions(geminiClient, fallbackBudget);
        org.mockito.Mockito.verify(claudeClient).extractRawJson("doc text");
    }

    @Test
    void useGeminiForNewsFalseNeverCallsGeminiEvenForNews() {
        DocumentIntelligenceEngine engineClaudeOnly = new DocumentIntelligenceEngine(claudeClient, geminiClient, fallbackBudget, new ObjectMapper(), false, 10000);
        when(claudeClient.extractRawJson("doc text")).thenReturn(VALID_JSON);

        engineClaudeOnly.classify("doc text", "NEWS");

        org.mockito.Mockito.verifyNoInteractions(geminiClient, fallbackBudget);
        org.mockito.Mockito.verify(claudeClient).extractRawJson("doc text");
    }

    @Test
    void shortDocumentsAreSentToStage1Unchanged() {
        assertThat(DocumentIntelligenceEngine.excerpt("short text", 10000)).isEqualTo("short text");
        assertThat(DocumentIntelligenceEngine.excerpt(null, 10000)).isNull();
    }

    @Test
    void aLongDocumentBecomesItsBeginningAndEndWithAnExplicitOmissionMarker() {
        String text = "A".repeat(8000) + "M".repeat(50000) + "Z".repeat(2000) + "TAILEND";

        String excerpt = DocumentIntelligenceEngine.excerpt(text, 10000);

        assertThat(excerpt).startsWith("A".repeat(8000));
        assertThat(excerpt).endsWith("TAILEND");
        assertThat(excerpt).doesNotContain("M");
        assertThat(excerpt).contains("characters omitted from the middle of this long document");
        assertThat(excerpt.length()).isLessThan(10300);
    }

    @Test
    void theOmissionMarkerStatesHowManyCharactersWereLeftOut() {
        String text = "x".repeat(60000);

        assertThat(DocumentIntelligenceEngine.excerpt(text, 10000)).contains("[... 50000 characters omitted");
    }

    @Test
    void aMaxCharsOfZeroDisablesTheExcerptEntirely() {
        String text = "x".repeat(60000);

        assertThat(DocumentIntelligenceEngine.excerpt(text, 0)).isEqualTo(text);
    }

    @Test
    void classifyHandsClaudeTheExcerptNotTheWholeDocument() {
        String longText = "HEAD" + "m".repeat(60000) + "TAIL";
        String excerpt = DocumentIntelligenceEngine.excerpt(longText, 10000);
        when(claudeClient.extractRawJson(excerpt)).thenReturn(VALID_JSON);

        engine.classify(longText, "EXCHANGE_ANNOUNCEMENT");

        org.mockito.Mockito.verify(claudeClient).extractRawJson(excerpt);
        org.mockito.Mockito.verify(claudeClient, org.mockito.Mockito.never()).extractRawJson(longText);
    }

    @Test
    void classifyHandsGeminiTheExcerptToo() {
        String longText = "HEAD" + "m".repeat(60000) + "TAIL";
        String excerpt = DocumentIntelligenceEngine.excerpt(longText, 10000);
        when(geminiClient.extractRawJson(excerpt)).thenReturn(VALID_JSON);

        engine.classify(longText, "NEWS");

        org.mockito.Mockito.verify(geminiClient).extractRawJson(excerpt);
    }

    @Test
    void thePromptSaysAnExcerptMayBeShownButDoesNotInviteExtraExtractorsJustInCase() {
        String prompt = DocumentIntelligenceEngine.buildPrompt("text");

        assertThat(prompt).contains("EXCERPT of a long document");
        assertThat(prompt).contains("do not add an extractor");
        assertThat(prompt).doesNotContain("even when that content isn't visible");
    }
}
