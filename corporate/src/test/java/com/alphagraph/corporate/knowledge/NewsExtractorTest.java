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

class NewsExtractorTest {

    private final NewsExtractor extractor = new NewsExtractor(mock(AnthropicClient.class), new ObjectMapper());

    @Test
    void supportsWhenRecommendedExtractorsContainsNews() {
        assertThat(extractor.supports(classification(List.of("NEWS")))).isTrue();
        assertThat(extractor.supports(classification(List.of("news")))).isTrue();
        assertThat(extractor.supports(classification(List.of("ORDER", "NEWS")))).isTrue();
    }

    @Test
    void doesNotSupportWhenNewsNotRecommended() {
        assertThat(extractor.supports(classification(List.of("ORDER")))).isFalse();
        assertThat(extractor.supports(classification(List.of()))).isFalse();
    }

    @Test
    void multipleCompaniesGetDistinctFactGroups() {
        String json = """
            {"impacts": [
              {"companyName": "Kaynes Technology", "direction": "POSITIVE", "signal": "PLI Beneficiary",
               "impactSummary": "Direct beneficiary of the new semiconductor PLI scheme.", "confidence": 90},
              {"companyName": "Dixon Technologies", "direction": "POSITIVE", "signal": "PLI Beneficiary",
               "impactSummary": "Expected to benefit from expanded component manufacturing incentives.", "confidence": 85}
            ]}
            """;

        ExtractionResult result = extractor.parseResult(json);

        List<UUID> groups = result.facts().stream().map(ExtractedFact::factGroup).distinct().toList();
        assertThat(groups).hasSize(2);
        assertThat(groups).doesNotContainNull();
    }

    @Test
    void parsesFullImpact() {
        String json = """
            {"impacts": [
              {"companyName": "Kaynes Technology", "direction": "POSITIVE", "signal": "PLI Beneficiary",
               "impactSummary": "Direct beneficiary of the new semiconductor PLI scheme.", "confidence": 90}
            ]}
            """;

        ExtractionResult result = extractor.parseResult(json);

        Map<String, ExtractedFact> byType = result.facts().stream()
            .collect(java.util.stream.Collectors.toMap(ExtractedFact::factType, f -> f));
        assertThat(byType.get("companyname").value()).isEqualTo("Kaynes Technology");
        assertThat(byType.get("direction").value()).isEqualTo("POSITIVE");
        assertThat(byType.get("signal").value()).isEqualTo("PLI Beneficiary");
        assertThat(byType.get("impactsummary").value()).isEqualTo("Direct beneficiary of the new semiconductor PLI scheme.");
        // commitmentLevel doesn't apply to news impacts (only forward-looking guidance has it).
        assertThat(result.facts()).extracting(ExtractedFact::commitmentLevel).allMatch(java.util.Objects::isNull);
        assertThat(result.facts().stream().map(ExtractedFact::factGroup).distinct()).hasSize(1);
    }

    @Test
    void parsesRelatedEntityAndRelationshipType() {
        String json = """
            {"impacts": [
              {"companyName": "Kaynes Technology", "direction": "POSITIVE", "signal": "PLI Beneficiary",
               "impactSummary": "Direct beneficiary of the new semiconductor PLI scheme.", "confidence": 90,
               "relatedEntityName": "Semiconductor PLI", "relatedEntityType": "GOVERNMENT_SCHEME", "relationshipType": "BENEFICIARY_OF"}
            ]}
            """;

        ExtractionResult result = extractor.parseResult(json);

        Map<String, ExtractedFact> byType = result.facts().stream()
            .collect(java.util.stream.Collectors.toMap(ExtractedFact::factType, f -> f));
        assertThat(byType.get("relatedentityname").value()).isEqualTo("Semiconductor PLI");
        assertThat(byType.get("relatedentitytype").value()).isEqualTo("GOVERNMENT_SCHEME");
        assertThat(byType.get("relationshiptype").value()).isEqualTo("BENEFICIARY_OF");
    }

    @Test
    void emptyRelatedEntityFieldsProduceNoRelatedEntityFacts() {
        String json = """
            {"impacts": [
              {"companyName": "Kaynes Technology", "direction": "POSITIVE", "signal": "PLI Beneficiary",
               "impactSummary": "Direct beneficiary of the new semiconductor PLI scheme.", "confidence": 90,
               "relatedEntityName": "", "relatedEntityType": "", "relationshipType": ""}
            ]}
            """;

        ExtractionResult result = extractor.parseResult(json);

        assertThat(result.facts()).extracting(ExtractedFact::factType)
            .doesNotContain("relatedentityname", "relatedentitytype", "relationshiptype");
    }

    @Test
    void impactMissingCompanyNameIsSkipped() {
        String json = """
            {"impacts": [
              {"companyName": "", "direction": "POSITIVE", "signal": "x", "impactSummary": "x", "confidence": 50}
            ]}
            """;

        assertThat(extractor.parseResult(json).facts()).isEmpty();
    }

    @Test
    void emptyImpactsListYieldsNoFacts() {
        String json = "{\"impacts\": []}";

        assertThat(extractor.parseResult(json).facts()).isEmpty();
    }

    @Test
    void malformedJsonThrowsIllegalStateException() {
        assertThatIllegalStateException()
            .isThrownBy(() -> extractor.parseResult("not valid json"))
            .withMessageContaining("Could not parse");
    }

    @Test
    void promptScopesOnlyToCompanyImpactFields() {
        String prompt = NewsExtractor.buildPrompt("Government announces new Semiconductor PLI scheme.");

        assertThat(prompt)
            .contains("companyName", "direction", "signal", "impactSummary")
            .contains("relatedEntityName", "relatedEntityType", "relationshipType", "BENEFICIARY_OF")
            .contains("Government announces new Semiconductor PLI scheme.");
    }

    @Test
    void outputConfigBuildsWithoutThrowing() {
        OutputConfig outputConfig = NewsExtractor.buildOutputConfig();

        assertThat(outputConfig).isNotNull();
    }

    private DocumentClassification classification(List<String> recommendedExtractors) {
        return new DocumentClassification(
            "NEWS", List.of(), List.of(), "x", Sentiment.NEUTRAL, 80.0, recommendedExtractors
        );
    }
}
