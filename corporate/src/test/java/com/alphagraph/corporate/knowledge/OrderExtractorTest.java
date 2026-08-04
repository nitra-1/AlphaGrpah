package com.alphagraph.corporate.knowledge;

import com.alphagraph.corporate.api.Sentiment;
import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;

/** Tests OrderExtractor's routing decision (supports()) and its own parsing/normalization logic, independent of DocumentIntelligenceEngine's Stage 1. */
class OrderExtractorTest {

    private final OrderExtractor extractor = new OrderExtractor(mock(AnthropicClient.class), new ObjectMapper());

    @Test
    void supportsWhenRecommendedExtractorsContainsOrder() {
        assertThat(extractor.supports(classification(List.of("ORDER")))).isTrue();
        assertThat(extractor.supports(classification(List.of("order")))).isTrue(); // case-insensitive
        assertThat(extractor.supports(classification(List.of("MANAGEMENT", "ORDER")))).isTrue();
    }

    @Test
    void doesNotSupportWhenOrderNotRecommended() {
        assertThat(extractor.supports(classification(List.of("MANAGEMENT")))).isFalse();
        assertThat(extractor.supports(classification(List.of()))).isFalse();
    }

    @Test
    void parsesFullOrderResponseAndComputesExecutionEnd() {
        String json = """
            {"customer": "Ministry of Defence", "orderValue": "2800", "currency": "CRORE",
             "businessUnit": "Electronics", "executionStartYear": "2026", "executionMonths": "36",
             "orderScope": "DOMESTIC", "orderSector": "GOVERNMENT", "orderRecurrence": "ONE_TIME",
             "orderLifecycleStage": "NEW_ORDER", "confidence": 99}
            """;

        ExtractionResult result = extractor.parseResult(json);

        Map<String, ExtractedFact> byType = result.facts().stream()
            .collect(java.util.stream.Collectors.toMap(ExtractedFact::factType, f -> f));
        assertThat(byType.get("customer").value()).isEqualTo("Ministry of Defence");
        assertThat(byType.get("ordervalue").value()).isEqualTo("2800");
        assertThat(byType.get("ordervalue").unit()).isEqualTo("CRORE");
        assertThat(byType.get("businessunit").value()).isEqualTo("Electronics");
        assertThat(byType.get("executionstart").value()).isEqualTo("2026");
        // 2026 start + 36 months (exactly 3 years) -> 2029
        assertThat(byType.get("executionend").value()).isEqualTo("2029");
        assertThat(byType.get("orderscope").value()).isEqualTo("DOMESTIC");
        assertThat(byType.get("ordersector").value()).isEqualTo("GOVERNMENT");
        assertThat(byType.get("orderrecurrence").value()).isEqualTo("ONE_TIME");
        assertThat(byType.get("orderlifecyclestage").value()).isEqualTo("NEW_ORDER");
        assertThat(byType.values()).allMatch(f -> f.extractionConfidence() == 99);
    }

    @Test
    void roundsUpPartialYearWhenComputingExecutionEnd() {
        // 2026 start + 30 months = 2.5 years -> rounds up to 2029, not 2028
        String json = """
            {"customer": "", "orderValue": "", "currency": "", "businessUnit": "",
             "executionStartYear": "2026", "executionMonths": "30",
             "orderScope": "", "orderSector": "", "orderRecurrence": "",
             "orderLifecycleStage": "NEW_ORDER", "confidence": 90}
            """;

        ExtractionResult result = extractor.parseResult(json);

        ExtractedFact executionEnd = result.facts().stream()
            .filter(f -> f.factType().equals("executionend")).findFirst().orElseThrow();
        assertThat(executionEnd.value()).isEqualTo("2029");
    }

    @Test
    void blankLifecycleStageMeansNotActuallyOrderRelatedAndYieldsNoFacts() {
        String json = """
            {"customer": "", "orderValue": "", "currency": "", "businessUnit": "",
             "executionStartYear": "", "executionMonths": "",
             "orderScope": "", "orderSector": "", "orderRecurrence": "",
             "orderLifecycleStage": "", "confidence": 40}
            """;

        ExtractionResult result = extractor.parseResult(json);

        assertThat(result.facts()).isEmpty();
    }

    @Test
    void missingExecutionMonthsLeavesExecutionEndAbsent() {
        String json = """
            {"customer": "Acme", "orderValue": "500", "currency": "CRORE", "businessUnit": "",
             "executionStartYear": "2026", "executionMonths": "",
             "orderScope": "", "orderSector": "", "orderRecurrence": "",
             "orderLifecycleStage": "COMPLETION", "confidence": 85}
            """;

        ExtractionResult result = extractor.parseResult(json);

        assertThat(result.facts()).extracting(ExtractedFact::factType).doesNotContain("executionend");
    }

    @Test
    void malformedJsonThrowsIllegalStateException() {
        assertThatIllegalStateException()
            .isThrownBy(() -> extractor.parseResult("not valid json"))
            .withMessageContaining("Could not parse");
    }

    @Test
    void promptScopesOnlyToOrderFields() {
        String prompt = OrderExtractor.buildPrompt("BEL received a Rs 2,800 Cr order.");

        assertThat(prompt)
            .contains("customer", "orderValue", "executionStartYear", "executionMonths", "orderLifecycleStage")
            .contains("BEL received a Rs 2,800 Cr order.");
    }

    @Test
    void outputConfigBuildsWithoutThrowing() {
        OutputConfig outputConfig = OrderExtractor.buildOutputConfig();

        assertThat(outputConfig).isNotNull();
    }

    private DocumentClassification classification(List<String> recommendedExtractors) {
        return new DocumentClassification(
            "ORDER_ANNOUNCEMENT", List.of(), List.of(), "x", Sentiment.NEUTRAL, 80.0, recommendedExtractors
        );
    }
}
