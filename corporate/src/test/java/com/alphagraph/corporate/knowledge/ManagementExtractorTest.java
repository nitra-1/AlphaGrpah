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

class ManagementExtractorTest {

    private final ManagementExtractor extractor = new ManagementExtractor(mock(AnthropicClient.class), new ObjectMapper());

    @Test
    void supportsWhenRecommendedExtractorsContainsManagement() {
        assertThat(extractor.supports(classification(List.of("MANAGEMENT")))).isTrue();
        assertThat(extractor.supports(classification(List.of("management")))).isTrue();
        assertThat(extractor.supports(classification(List.of("ORDER", "MANAGEMENT")))).isTrue();
    }

    @Test
    void doesNotSupportWhenManagementNotRecommended() {
        assertThat(extractor.supports(classification(List.of("ORDER")))).isFalse();
        assertThat(extractor.supports(classification(List.of()))).isFalse();
    }

    @Test
    void multipleStatementsGetDistinctFactGroups() {
        String json = """
            {"statements": [
              {"metricType": "REVENUE_GUIDANCE", "valueText": "30%", "valueNumeric": "30", "period": "next two years",
               "direction": "POSITIVE", "signal": "Growth Visibility", "commitmentLevel": "HIGH", "confidence": 95},
              {"metricType": "MARGIN_GUIDANCE", "valueText": "improving by 100bps", "valueNumeric": "",
               "period": "FY27", "direction": "POSITIVE", "signal": "Margin Expansion", "commitmentLevel": "MEDIUM", "confidence": 85}
            ]}
            """;

        ExtractionResult result = extractor.parseResult(json);

        List<UUID> groups = result.facts().stream().map(ExtractedFact::factGroup).distinct().toList();
        assertThat(groups).hasSize(2);
        assertThat(groups).doesNotContainNull();
    }

    @Test
    void parsesFullStatementWithCommitmentOnPrimaryFact() {
        String json = """
            {"statements": [
              {"metricType": "REVENUE_GUIDANCE", "valueText": "30%", "valueNumeric": "30", "period": "next two years",
               "direction": "POSITIVE", "signal": "Growth Visibility", "commitmentLevel": "HIGH", "confidence": 95}
            ]}
            """;

        ExtractionResult result = extractor.parseResult(json);

        Map<String, ExtractedFact> byType = result.facts().stream()
            .collect(java.util.stream.Collectors.toMap(ExtractedFact::factType, f -> f));
        assertThat(byType.get("metrictype").value()).isEqualTo("REVENUE_GUIDANCE");
        assertThat(byType.get("metrictype").commitmentLevel()).isEqualTo("HIGH");
        assertThat(byType.get("guidancevalue").value()).isEqualTo("30%");
        assertThat(byType.get("guidancevaluenumeric").value()).isEqualTo("30");
        assertThat(byType.get("guidanceperiod").value()).isEqualTo("next two years");
        assertThat(byType.get("direction").value()).isEqualTo("POSITIVE");
        assertThat(byType.get("signal").value()).isEqualTo("Growth Visibility");
        // Every fact in the group shares the same correlation id.
        assertThat(result.facts()).extracting(ExtractedFact::factGroup).doesNotContainNull();
        assertThat(result.facts().stream().map(ExtractedFact::factGroup).distinct()).hasSize(1);
    }

    @Test
    void qualitativeStatementWithoutNumericValueOmitsThatFact() {
        String json = """
            {"statements": [
              {"metricType": "DEMAND", "valueText": "strong domestic demand", "valueNumeric": "",
               "period": "", "direction": "POSITIVE", "signal": "Demand Strength", "commitmentLevel": "MEDIUM", "confidence": 80}
            ]}
            """;

        ExtractionResult result = extractor.parseResult(json);

        assertThat(result.facts()).extracting(ExtractedFact::factType).doesNotContain("guidancevaluenumeric", "guidanceperiod");
    }

    @Test
    void emptyStatementsListYieldsNoFacts() {
        String json = "{\"statements\": []}";

        assertThat(extractor.parseResult(json).facts()).isEmpty();
    }

    @Test
    void malformedJsonThrowsIllegalStateException() {
        assertThatIllegalStateException()
            .isThrownBy(() -> extractor.parseResult("not valid json"))
            .withMessageContaining("Could not parse");
    }

    @Test
    void promptScopesOnlyToManagementCommentaryFields() {
        String prompt = ManagementExtractor.buildPrompt("We expect 30% revenue growth over the next two years.");

        assertThat(prompt)
            .contains("REVENUE_GUIDANCE", "MARGIN_GUIDANCE", "CAPEX", "DEMAND", "PRICING", "COMPETITION", "HIRING", "EXPORTS", "RISK")
            .contains("commitmentLevel", "LOW", "VERY_HIGH")
            .contains("We expect 30% revenue growth over the next two years.");
    }

    @Test
    void outputConfigBuildsWithoutThrowing() {
        OutputConfig outputConfig = ManagementExtractor.buildOutputConfig();

        assertThat(outputConfig).isNotNull();
    }

    private DocumentClassification classification(List<String> recommendedExtractors) {
        return new DocumentClassification(
            "MANAGEMENT_COMMENTARY", List.of(), List.of(), "x", Sentiment.NEUTRAL, 80.0, recommendedExtractors
        );
    }
}
