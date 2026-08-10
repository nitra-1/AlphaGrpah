package com.alphagraph.corporate.knowledge;

import com.alphagraph.corporate.api.Sentiment;
import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;

class FinancialResultsExtractorTest {

    private final FinancialResultsExtractor extractor = new FinancialResultsExtractor(mock(AnthropicClient.class), new ObjectMapper());

    @Test
    void supportsWhenRecommendedExtractorsContainsFinancialResults() {
        assertThat(extractor.supports(classification(List.of("FINANCIAL_RESULTS")))).isTrue();
        assertThat(extractor.supports(classification(List.of("financial_results")))).isTrue();
        assertThat(extractor.supports(classification(List.of("ORDER", "FINANCIAL_RESULTS")))).isTrue();
    }

    @Test
    void doesNotSupportWhenFinancialResultsNotRecommended() {
        assertThat(extractor.supports(classification(List.of("ORDER")))).isFalse();
        assertThat(extractor.supports(classification(List.of()))).isFalse();
    }

    @Test
    void multiplePeriodsGetDistinctFactGroups() {
        String json = """
            {"periods": [
              %s,
              %s
            ]}
            """.formatted(period("2026-03-31", "QUARTERLY", "CRORE", "1300.5", "103.3"), period("2026-03-31", "ANNUAL", "CRORE", "4763.1", "393.9"));

        ExtractionResult result = extractor.parseResult(json);

        List<UUID> groups = result.facts().stream().map(ExtractedFact::factGroup).distinct().toList();
        assertThat(groups).hasSize(2);
        assertThat(groups).doesNotContainNull();
    }

    @Test
    void parsesRequiredFieldsForOnePeriod() {
        String json = "{\"periods\": [" + period("2026-03-31", "QUARTERLY", "CRORE", "1300.5", "103.3") + "]}";

        ExtractionResult result = extractor.parseResult(json);

        Map<String, ExtractedFact> byType = result.facts().stream()
            .collect(java.util.stream.Collectors.toMap(ExtractedFact::factType, f -> f));
        assertThat(byType.get("periodend").value()).isEqualTo("2026-03-31");
        assertThat(byType.get("periodtype").value()).isEqualTo("QUARTERLY");
        assertThat(new BigDecimal(byType.get("sales").value())).isEqualByComparingTo("1300.50");
        assertThat(new BigDecimal(byType.get("pat").value())).isEqualByComparingTo("103.30");
        assertThat(byType.get("eps").value()).isEqualTo("3.22");
    }

    @Test
    void monetaryFieldsAreConvertedFromMillionToCrore() {
        String json = "{\"periods\": [" + period("2026-03-31", "QUARTERLY", "MILLION", "13005", "1033") + "]}";

        ExtractionResult result = extractor.parseResult(json);

        Map<String, ExtractedFact> byType = result.facts().stream()
            .collect(java.util.stream.Collectors.toMap(ExtractedFact::factType, f -> f));
        // 13,005 million = 1,300.5 crore (Million -> Crore is divide by 10)
        assertThat(new BigDecimal(byType.get("sales").value())).isEqualByComparingTo("1300.50");
        assertThat(new BigDecimal(byType.get("pat").value())).isEqualByComparingTo("103.30");
    }

    @Test
    void epsAndPercentagesAreNeverUnitConverted() {
        String json = "{\"periods\": [" + period("2026-03-31", "QUARTERLY", "MILLION", "13005", "1033") + "]}";

        ExtractionResult result = extractor.parseResult(json);

        Map<String, ExtractedFact> byType = result.facts().stream()
            .collect(java.util.stream.Collectors.toMap(ExtractedFact::factType, f -> f));
        assertThat(byType.get("eps").value()).isEqualTo("3.22");
        assertThat(byType.get("roepercentage").value()).isEqualTo("15.5");
    }

    @Test
    void periodMissingSalesIsSkippedEntirely() {
        String json = """
            {"periods": [
              {"periodEnd": "2026-03-31", "periodType": "QUARTERLY", "sourceUnit": "CRORE",
               "sales": "", "pat": "103.3", "eps": "3.22", "roePercentage": "", "rocePercentage": "",
               "operatingMarginPercentage": "", "netMarginPercentage": "", "cashFlowFromOperations": "",
               "totalAssets": "", "currentAssets": "", "currentLiabilities": "", "totalDebt": "",
               "totalEquity": "", "interestExpense": "", "ebit": "", "confidence": 90}
            ]}
            """;

        assertThat(extractor.parseResult(json).facts()).isEmpty();
    }

    @Test
    void periodWithUnparseablePeriodEndIsSkippedEntirely() {
        String json = """
            {"periods": [
              {"periodEnd": "31st March 2026", "periodType": "QUARTERLY", "sourceUnit": "CRORE",
               "sales": "1300.5", "pat": "103.3", "eps": "3.22", "roePercentage": "", "rocePercentage": "",
               "operatingMarginPercentage": "", "netMarginPercentage": "", "cashFlowFromOperations": "",
               "totalAssets": "", "currentAssets": "", "currentLiabilities": "", "totalDebt": "",
               "totalEquity": "", "interestExpense": "", "ebit": "", "confidence": 90}
            ]}
            """;

        assertThat(extractor.parseResult(json).facts()).isEmpty();
    }

    @Test
    void optionalBalanceSheetFieldsAreOmittedWhenBlank() {
        String json = "{\"periods\": [" + period("2026-03-31", "QUARTERLY", "CRORE", "1300.5", "103.3") + "]}";

        ExtractionResult result = extractor.parseResult(json);

        assertThat(result.facts()).extracting(ExtractedFact::factType)
            .doesNotContain("totalassets", "currentassets", "currentliabilities", "totaldebt", "totalequity", "interestexpense", "ebit", "cashflowfromoperations");
    }

    @Test
    void balanceSheetFieldsArePresentAndConvertedWhenStated() {
        String json = """
            {"periods": [
              {"periodEnd": "2026-03-31", "periodType": "QUARTERLY", "sourceUnit": "MILLION",
               "sales": "13005", "pat": "1033", "eps": "3.22", "roePercentage": "", "rocePercentage": "",
               "operatingMarginPercentage": "", "netMarginPercentage": "", "cashFlowFromOperations": "12380",
               "totalAssets": "489980", "currentAssets": "211910", "currentLiabilities": "135630",
               "totalDebt": "70600", "totalEquity": "286640", "interestExpense": "2930", "ebit": "",
               "confidence": 90}
            ]}
            """;

        ExtractionResult result = extractor.parseResult(json);

        Map<String, ExtractedFact> byType = result.facts().stream()
            .collect(java.util.stream.Collectors.toMap(ExtractedFact::factType, f -> f));
        assertThat(new BigDecimal(byType.get("cashflowfromoperations").value())).isEqualByComparingTo("1238.00");
        assertThat(new BigDecimal(byType.get("totalassets").value())).isEqualByComparingTo("48998.00");
        assertThat(byType).doesNotContainKey("ebit");
    }

    @Test
    void toCroreHandlesAllFourUnits() {
        assertThat(FinancialResultsExtractor.toCrore("100", "CRORE")).isEqualByComparingTo("100.00");
        assertThat(FinancialResultsExtractor.toCrore("1000", "MILLION")).isEqualByComparingTo("100.00");
        assertThat(FinancialResultsExtractor.toCrore("10000", "LAKH")).isEqualByComparingTo("100.00");
        assertThat(FinancialResultsExtractor.toCrore("1000000000", "ABSOLUTE")).isEqualByComparingTo("100.00");
    }

    @Test
    void toCroreReturnsNullForUnparseableOrUnknownUnit() {
        assertThat(FinancialResultsExtractor.toCrore("not-a-number", "CRORE")).isNull();
        assertThat(FinancialResultsExtractor.toCrore("100", "UNKNOWN")).isNull();
        assertThat(FinancialResultsExtractor.toCrore("", "CRORE")).isNull();
    }

    @Test
    void emptyPeriodsListYieldsNoFacts() {
        assertThat(extractor.parseResult("{\"periods\": []}").facts()).isEmpty();
    }

    @Test
    void malformedJsonThrowsIllegalStateException() {
        assertThatIllegalStateException()
            .isThrownBy(() -> extractor.parseResult("not valid json"))
            .withMessageContaining("Could not parse");
    }

    @Test
    void promptScopesOnlyToFinancialResultsFields() {
        String prompt = FinancialResultsExtractor.buildPrompt("Statement of Consolidated Financial Results for the Quarter ended June 30, 2026.");

        assertThat(prompt)
            .contains("Sales", "Profit", "EPS", "CONSOLIDATED")
            .contains("QUARTERLY", "ANNUAL")
            .contains("CRORE", "MILLION", "LAKH", "ABSOLUTE")
            .contains("Six months ended", "SKIP")
            .contains("Statement of Consolidated Financial Results for the Quarter ended June 30, 2026.");
    }

    @Test
    void outputConfigBuildsWithoutThrowing() {
        OutputConfig outputConfig = FinancialResultsExtractor.buildOutputConfig();

        assertThat(outputConfig).isNotNull();
    }

    private static String period(String periodEnd, String periodType, String sourceUnit, String sales, String pat) {
        return """
            {"periodEnd": "%s", "periodType": "%s", "sourceUnit": "%s", "sales": "%s", "pat": "%s",
             "eps": "3.22", "roePercentage": "15.5", "rocePercentage": "", "operatingMarginPercentage": "",
             "netMarginPercentage": "", "cashFlowFromOperations": "", "totalAssets": "", "currentAssets": "",
             "currentLiabilities": "", "totalDebt": "", "totalEquity": "", "interestExpense": "", "ebit": "",
             "confidence": 90}
            """.formatted(periodEnd, periodType, sourceUnit, sales, pat);
    }

    private DocumentClassification classification(List<String> recommendedExtractors) {
        return new DocumentClassification(
            "FINANCIAL_RESULT", List.of(), List.of(), "x", Sentiment.NEUTRAL, 80.0, recommendedExtractors
        );
    }
}
