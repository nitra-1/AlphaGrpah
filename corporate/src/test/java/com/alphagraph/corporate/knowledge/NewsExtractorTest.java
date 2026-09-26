package com.alphagraph.corporate.knowledge;

import com.alphagraph.corporate.api.Sentiment;
import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NewsExtractorTest {

    private final ClaudeNewsExtractionClient claudeClient = mock(ClaudeNewsExtractionClient.class);
    private final GeminiNewsExtractionClient geminiClient = mock(GeminiNewsExtractionClient.class);
    private final NewsExtractor extractor = new NewsExtractor(claudeClient, geminiClient, new ObjectMapper(), true);

    private static final String EVENT_HEADER = """
        "economicRelevance": "ECONOMIC", "theme": "DEFENCE_SPENDING", "eventDirection": "POSITIVE",
        "magnitude": "HIGH", "eventConfidence": 88, "horizon": "MEDIUM_TERM",
        """;

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
    void eventLevelClassificationBecomesItsOwnFactGroup() {
        String json = "{" + EVENT_HEADER + """
            "sectorImpacts": [], "impacts": []}
            """;

        ExtractionResult result = extractor.parseResult(json);

        Map<String, ExtractedFact> byType = result.facts().stream()
            .collect(java.util.stream.Collectors.toMap(ExtractedFact::factType, f -> f));
        assertThat(byType.get("economicrelevance").value()).isEqualTo("ECONOMIC");
        assertThat(byType.get("theme").value()).isEqualTo("DEFENCE_SPENDING");
        assertThat(byType.get("eventdirection").value()).isEqualTo("POSITIVE");
        assertThat(byType.get("magnitude").value()).isEqualTo("HIGH");
        assertThat(byType.get("horizon").value()).isEqualTo("MEDIUM_TERM");
        assertThat(result.facts()).allMatch(f -> f.extractionConfidence() == 88.0);
        assertThat(result.facts().stream().map(ExtractedFact::factGroup).distinct()).hasSize(1);
    }

    @Test
    void nonEconomicClassificationStillProducesTheEventGroupEvenWithEmptyLists() {
        String json = """
            {"economicRelevance": "NON_ECONOMIC", "theme": "", "eventDirection": "NEUTRAL",
             "magnitude": "LOW", "eventConfidence": 95, "horizon": "SHORT_TERM",
             "sectorImpacts": [], "impacts": []}
            """;

        ExtractionResult result = extractor.parseResult(json);

        assertThat(result.facts()).extracting(ExtractedFact::factType).contains("economicrelevance");
        assertThat(result.facts()).extracting(ExtractedFact::factType).doesNotContain("theme");
    }

    @Test
    void oneEventCanProduceBothPositiveAndNegativeSectorImpacts() {
        // crude oil rising: Airlines NEGATIVE, Oil Producers POSITIVE - never collapsed to one verdict
        String json = "{" + EVENT_HEADER + """
            "sectorImpacts": [
              {"sector": "Airlines", "direction": "NEGATIVE", "strength": "HIGH", "confidence": 85, "mechanism": "higher fuel cost"},
              {"sector": "Oil Producers", "direction": "POSITIVE", "strength": "HIGH", "confidence": 85, "mechanism": "higher realizations"}
            ], "impacts": []}
            """;

        ExtractionResult result = extractor.parseResult(json);

        List<ExtractedFact> sectorNameFacts = result.facts().stream().filter(f -> f.factType().equals("sectorname")).toList();
        assertThat(sectorNameFacts).extracting(ExtractedFact::value).containsExactlyInAnyOrder("Airlines", "Oil Producers");

        Map<UUID, List<ExtractedFact>> byGroup = result.facts().stream()
            .filter(f -> f.factGroup() != null)
            .collect(java.util.stream.Collectors.groupingBy(ExtractedFact::factGroup));
        List<String> sectorDirections = byGroup.values().stream()
            .filter(group -> group.stream().anyMatch(f -> f.factType().equals("sectorname")))
            .map(group -> group.stream().filter(f -> f.factType().equals("sectordirection")).findFirst().orElseThrow().value())
            .toList();
        assertThat(sectorDirections).containsExactlyInAnyOrder("NEGATIVE", "POSITIVE");
    }

    @Test
    void sectorImpactMissingRequiredFieldIsSkipped() {
        String json = "{" + EVENT_HEADER + """
            "sectorImpacts": [
              {"sector": "", "direction": "NEGATIVE", "strength": "HIGH", "confidence": 85, "mechanism": ""}
            ], "impacts": []}
            """;

        ExtractionResult result = extractor.parseResult(json);

        assertThat(result.facts()).extracting(ExtractedFact::factType).doesNotContain("sectorname");
    }

    @Test
    void multipleCompaniesGetDistinctFactGroups() {
        String json = "{" + EVENT_HEADER + """
            "sectorImpacts": [], "impacts": [
              {"companyName": "Kaynes Technology", "direction": "POSITIVE", "signal": "PLI Beneficiary",
               "impactSummary": "Direct beneficiary of the new semiconductor PLI scheme.", "confidence": 90,
               "sector": "Electronics", "exposureType": "REGULATORY", "impactStrength": "HIGH"},
              {"companyName": "Dixon Technologies", "direction": "POSITIVE", "signal": "PLI Beneficiary",
               "impactSummary": "Expected to benefit from expanded component manufacturing incentives.", "confidence": 85,
               "sector": "Electronics", "exposureType": "REGULATORY", "impactStrength": "MEDIUM"}
            ]}
            """;

        ExtractionResult result = extractor.parseResult(json);

        List<UUID> companyGroups = result.facts().stream()
            .filter(f -> f.factType().equals("companyname"))
            .map(ExtractedFact::factGroup).distinct().toList();
        assertThat(companyGroups).hasSize(2);
        assertThat(companyGroups).doesNotContainNull();
    }

    @Test
    void parsesFullImpactIncludingSectorAndExposureType() {
        String json = "{" + EVENT_HEADER + """
            "sectorImpacts": [], "impacts": [
              {"companyName": "Kaynes Technology", "direction": "POSITIVE", "signal": "PLI Beneficiary",
               "impactSummary": "Direct beneficiary of the new semiconductor PLI scheme.", "confidence": 90,
               "sector": "Electronics", "exposureType": "REGULATORY", "impactStrength": "HIGH"}
            ]}
            """;

        ExtractionResult result = extractor.parseResult(json);

        Map<String, ExtractedFact> byType = result.facts().stream()
            .filter(f -> f.factType().equals("companyname") || f.factType().equals("sector") || f.factType().equals("exposuretype")
                || f.factType().equals("impactstrength") || f.factType().equals("direction") || f.factType().equals("signal")
                || f.factType().equals("impactsummary"))
            .collect(java.util.stream.Collectors.toMap(ExtractedFact::factType, f -> f, (a, b) -> a));
        assertThat(byType.get("companyname").value()).isEqualTo("Kaynes Technology");
        assertThat(byType.get("direction").value()).isEqualTo("POSITIVE");
        assertThat(byType.get("signal").value()).isEqualTo("PLI Beneficiary");
        assertThat(byType.get("impactsummary").value()).isEqualTo("Direct beneficiary of the new semiconductor PLI scheme.");
        assertThat(byType.get("sector").value()).isEqualTo("Electronics");
        assertThat(byType.get("exposuretype").value()).isEqualTo("REGULATORY");
        assertThat(byType.get("impactstrength").value()).isEqualTo("HIGH");
        // commitmentLevel doesn't apply to news impacts (only forward-looking guidance has it).
        assertThat(result.facts()).extracting(ExtractedFact::commitmentLevel).allMatch(java.util.Objects::isNull);
    }

    @Test
    void parsesRelatedEntityAndRelationshipType() {
        String json = "{" + EVENT_HEADER + """
            "sectorImpacts": [], "impacts": [
              {"companyName": "Kaynes Technology", "direction": "POSITIVE", "signal": "PLI Beneficiary",
               "impactSummary": "Direct beneficiary of the new semiconductor PLI scheme.", "confidence": 90,
               "sector": "Electronics", "exposureType": "REGULATORY", "impactStrength": "HIGH",
               "relatedEntityName": "Semiconductor PLI", "relatedEntityType": "GOVERNMENT_SCHEME", "relationshipType": "BENEFICIARY_OF"}
            ]}
            """;

        ExtractionResult result = extractor.parseResult(json);

        Map<String, ExtractedFact> byType = result.facts().stream()
            .filter(f -> f.factType().startsWith("relat"))
            .collect(java.util.stream.Collectors.toMap(ExtractedFact::factType, f -> f));
        assertThat(byType.get("relatedentityname").value()).isEqualTo("Semiconductor PLI");
        assertThat(byType.get("relatedentitytype").value()).isEqualTo("GOVERNMENT_SCHEME");
        assertThat(byType.get("relationshiptype").value()).isEqualTo("BENEFICIARY_OF");
    }

    @Test
    void emptyRelatedEntityFieldsProduceNoRelatedEntityFacts() {
        String json = "{" + EVENT_HEADER + """
            "sectorImpacts": [], "impacts": [
              {"companyName": "Kaynes Technology", "direction": "POSITIVE", "signal": "PLI Beneficiary",
               "impactSummary": "Direct beneficiary of the new semiconductor PLI scheme.", "confidence": 90,
               "sector": "Electronics", "exposureType": "REGULATORY", "impactStrength": "HIGH",
               "relatedEntityName": "", "relatedEntityType": "", "relationshipType": ""}
            ]}
            """;

        ExtractionResult result = extractor.parseResult(json);

        assertThat(result.facts()).extracting(ExtractedFact::factType)
            .doesNotContain("relatedentityname", "relatedentitytype", "relationshiptype");
    }

    @Test
    void impactMissingCompanyNameIsSkipped() {
        String json = "{" + EVENT_HEADER + """
            "sectorImpacts": [], "impacts": [
              {"companyName": "", "direction": "POSITIVE", "signal": "x", "impactSummary": "x", "confidence": 50,
               "sector": "x", "exposureType": "DIRECT", "impactStrength": "LOW"}
            ]}
            """;

        assertThat(extractor.parseResult(json).facts()).extracting(ExtractedFact::factType).doesNotContain("companyname");
    }

    @Test
    void emptyImpactsListYieldsNoCompanyFacts() {
        String json = "{" + EVENT_HEADER + "\"sectorImpacts\": [], \"impacts\": []}";

        assertThat(extractor.parseResult(json).facts()).extracting(ExtractedFact::factType).doesNotContain("companyname");
    }

    @Test
    void missingSectorImpactsAndImpactsFieldsDoNotThrow() {
        // Old-shaped JSON (pre-rework) - Jackson leaves missing list fields null; parseResult must
        // not NPE on a response that only carries the (still-required) event-level fields.
        String json = """
            {"economicRelevance": "ECONOMIC", "theme": "DEFENCE_SPENDING", "eventDirection": "POSITIVE",
             "magnitude": "HIGH", "eventConfidence": 88, "horizon": "MEDIUM_TERM"}
            """;

        assertThat(extractor.parseResult(json).facts()).isNotEmpty(); // event-level facts still present
    }

    @Test
    void malformedJsonThrowsIllegalStateException() {
        assertThatIllegalStateException()
            .isThrownBy(() -> extractor.parseResult("not valid json"))
            .withMessageContaining("Could not parse");
    }

    @Test
    void promptCoversEventSectorAndCompanyFields() {
        String prompt = NewsExtractor.buildPrompt("Government announces new Semiconductor PLI scheme.");

        assertThat(prompt)
            .contains("economicRelevance", "theme", "eventDirection", "magnitude", "horizon")
            .contains("sectorImpacts", "sector", "strength", "mechanism")
            .contains("companyName", "direction", "signal", "impactSummary", "exposureType")
            .contains("relatedEntityName", "relatedEntityType", "relationshipType", "BENEFICIARY_OF")
            .contains("Government announces new Semiconductor PLI scheme.");
    }

    @Test
    void outputConfigBuildsWithoutThrowing() {
        OutputConfig outputConfig = NewsExtractor.buildOutputConfig();

        assertThat(outputConfig).isNotNull();
    }

    @Test
    void schemaMapIsWhatBothProvidersActuallyGetAsked() {
        Map<String, Object> schemaMap = NewsExtractor.buildSchemaMap();

        assertThat(schemaMap.get("type")).isEqualTo("object");
        assertThat(schemaMap.get("additionalProperties")).isEqualTo(false);
        assertThat(schemaMap.get("required")).isEqualTo(List.of(
            "economicRelevance", "theme", "eventDirection", "magnitude", "eventConfidence", "horizon",
            "sectorImpacts", "impacts"
        ));
    }

    @Test
    void useGeminiTrueAndGeminiSucceedsNeverCallsClaude() {
        NewsExtractor extractorWithGemini = new NewsExtractor(claudeClient, geminiClient, new ObjectMapper(), true);
        String json = "{" + EVENT_HEADER + "\"sectorImpacts\": [], \"impacts\": []}";
        when(geminiClient.extractRawJson("doc text")).thenReturn(json);

        ExtractionResult result = extractorWithGemini.extract(context("doc text"));

        assertThat(result.facts()).isNotEmpty();
        org.mockito.Mockito.verify(geminiClient).extractRawJson("doc text");
        org.mockito.Mockito.verifyNoInteractions(claudeClient);
    }

    @Test
    void useGeminiTrueAndGeminiFailsFallsBackToClaude() {
        NewsExtractor extractorWithGemini = new NewsExtractor(claudeClient, geminiClient, new ObjectMapper(), true);
        when(geminiClient.extractRawJson("doc text")).thenThrow(new IllegalStateException("Gemini API call failed"));
        when(claudeClient.extractRawJson("doc text")).thenReturn("{" + EVENT_HEADER + """
            "sectorImpacts": [], "impacts": [{"companyName": "TCS", "direction": "POSITIVE", "signal": "x",
             "impactSummary": "x", "confidence": 90, "sector": "IT", "exposureType": "DIRECT", "impactStrength": "MEDIUM"}]}
            """);

        ExtractionResult result = extractorWithGemini.extract(context("doc text"));

        assertThat(result.facts()).isNotEmpty();
        org.mockito.Mockito.verify(claudeClient).extractRawJson("doc text");
    }

    @Test
    void useGeminiFalseNeverCallsGeminiAtAll() {
        NewsExtractor extractorClaudeOnly = new NewsExtractor(claudeClient, geminiClient, new ObjectMapper(), false);
        String json = "{" + EVENT_HEADER + "\"sectorImpacts\": [], \"impacts\": []}";
        when(claudeClient.extractRawJson("doc text")).thenReturn(json);

        extractorClaudeOnly.extract(context("doc text"));

        org.mockito.Mockito.verifyNoInteractions(geminiClient);
        org.mockito.Mockito.verify(claudeClient).extractRawJson("doc text");
    }

    private DocumentContext context(String documentText) {
        return new DocumentContext(UUID.randomUUID(), UUID.randomUUID(), "TCS", documentText, classification(List.of("NEWS")));
    }

    private DocumentClassification classification(List<String> recommendedExtractors) {
        return new DocumentClassification(
            "NEWS", List.of(), List.of(), "x", Sentiment.NEUTRAL, 80.0, recommendedExtractors
        );
    }
}
