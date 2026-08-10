package com.alphagraph.corporate.knowledge;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stage 2: knows only about a company's own quarterly/annual Financial Results filings (a Board
 * Meeting Outcome under SEBI LODR Regulation 33) - Sales, PAT, EPS, and the balance-sheet/margin
 * fields {@code financial.financial_results} already models. A single filing's table always
 * presents several period columns at once (current quarter, prior quarter, year-ago quarter, full
 * year), each becoming its own {@code fact_group} - the same multi-record-per-document shape
 * {@link ManagementExtractor} established, reused here because one Financial Results PDF can
 * retroactively fill several quarters' worth of previously-missing data at once.
 *
 * <p>Real, disclosed judgment call: when a filing shows both Standalone and Consolidated figures
 * side by side (common for a listed company with subsidiaries), the prompt asks for Consolidated -
 * the figure that represents the whole corporate group, matching how the group's performance is
 * conventionally referenced. Standalone-only filings (no subsidiaries) use whatever's presented.
 *
 * <p>Monetary figures are extracted in whatever unit the table header states (Crore, Million, or
 * Lakh - Indian filings aren't consistent) and converted to Crore deterministically in Java by
 * {@link #toCrore}, never asked of the model as arithmetic - the same discipline
 * {@code OrderExtractor.computeExecutionEndYear} already established for execution-end-year math.
 * EPS and every percentage field are never unit-converted.
 *
 * <p>These facts land in {@code corporate.document_facts} like every other extractor's output -
 * {@code corporate} cannot depend on {@code financial} (module boundary rule,
 * docs/001_System_Architecture.md §4), so getting a period from here into
 * {@code financial.financial_results} is {@code intelligence.financial.FinancialResultsBridgeOrchestrator}'s
 * job, reading these facts back out via {@link FinancialResultFactReader}.
 */
@Component
class FinancialResultsExtractor implements DocumentExtractor {

    private static final Model MODEL = Model.CLAUDE_SONNET_5;
    private static final long MAX_TOKENS = 4096L;

    private final AnthropicClient client;
    private final ObjectMapper objectMapper;

    FinancialResultsExtractor(AnthropicClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(DocumentClassification classification) {
        return classification.recommendedExtractors().stream()
            .anyMatch(name -> name.equalsIgnoreCase("FINANCIAL_RESULTS"));
    }

    @Override
    public ExtractionResult extract(DocumentContext context) {
        MessageCreateParams createParams = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(MAX_TOKENS)
            .outputConfig(buildOutputConfig())
            .addUserMessage(buildPrompt(context.documentText()))
            .build();

        String rawJson = callClaude(createParams);
        return parseResult(rawJson);
    }

    private String callClaude(MessageCreateParams createParams) {
        try {
            StringBuilder rawJson = new StringBuilder();
            client.messages().create(createParams).content().stream()
                .flatMap(contentBlock -> contentBlock.text().stream())
                .forEach(textBlock -> rawJson.append(textBlock.text()));
            return rawJson.toString();
        } catch (NotFoundException e) {
            throw new IllegalStateException("Claude API rejected the model/endpoint: " + e.getMessage(), e);
        } catch (RateLimitException e) {
            throw new IllegalStateException("Claude API rate limit hit during financial results extraction: " + e.getMessage(), e);
        } catch (AnthropicIoException e) {
            throw new IllegalStateException("Network failure calling Claude API: " + e.getMessage(), e);
        } catch (AnthropicServiceException e) {
            throw new IllegalStateException("Claude API call failed: " + e.getMessage(), e);
        }
    }

    ExtractionResult parseResult(String rawJson) {
        LlmFinancialResultsExtractionResponse response;
        try {
            response = objectMapper.readValue(rawJson, LlmFinancialResultsExtractionResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse Claude's structured-output JSON: " + rawJson, e);
        }

        List<ExtractedFact> facts = new ArrayList<>();
        for (LlmFinancialResultPeriod period : response.periods()) {
            addPeriod(facts, period);
        }
        return new ExtractionResult(facts);
    }

    private static void addPeriod(List<ExtractedFact> facts, LlmFinancialResultPeriod period) {
        // periodEnd/periodType/sales/pat are the four NOT NULL columns on financial.financial_results
        // (financial.api.FinancialResult) - a period missing any of these can't become a valid row,
        // so it's skipped entirely rather than written as a partial/malformed record. Same "zero is
        // a valid outcome" precedent as every other Stage 2 consumer in this codebase.
        LocalDate periodEnd = parseDate(period.periodEnd());
        if (periodEnd == null || isBlank(period.periodType()) || isBlank(period.sales()) || isBlank(period.pat())) {
            return;
        }

        BigDecimal sales = toCrore(period.sales(), period.sourceUnit());
        BigDecimal pat = toCrore(period.pat(), period.sourceUnit());
        if (sales == null || pat == null) {
            return;
        }

        UUID group = UUID.randomUUID();
        double confidence = period.confidence();

        addRaw(facts, "periodend", periodEnd.toString(), confidence, group);
        addRaw(facts, "periodtype", period.periodType().trim().toUpperCase(), confidence, group);
        addRaw(facts, "sales", sales.toPlainString(), confidence, group);
        addRaw(facts, "pat", pat.toPlainString(), confidence, group);
        addIfPresent(facts, "eps", period.eps(), confidence, group);
        addIfPresent(facts, "roepercentage", period.roePercentage(), confidence, group);
        addIfPresent(facts, "rocepercentage", period.rocePercentage(), confidence, group);
        addIfPresent(facts, "operatingmarginpercentage", period.operatingMarginPercentage(), confidence, group);
        addIfPresent(facts, "netmarginpercentage", period.netMarginPercentage(), confidence, group);
        addConverted(facts, "cashflowfromoperations", period.cashFlowFromOperations(), period.sourceUnit(), confidence, group);
        addConverted(facts, "totalassets", period.totalAssets(), period.sourceUnit(), confidence, group);
        addConverted(facts, "currentassets", period.currentAssets(), period.sourceUnit(), confidence, group);
        addConverted(facts, "currentliabilities", period.currentLiabilities(), period.sourceUnit(), confidence, group);
        addConverted(facts, "totaldebt", period.totalDebt(), period.sourceUnit(), confidence, group);
        addConverted(facts, "totalequity", period.totalEquity(), period.sourceUnit(), confidence, group);
        addConverted(facts, "interestexpense", period.interestExpense(), period.sourceUnit(), confidence, group);
        addConverted(facts, "ebit", period.ebit(), period.sourceUnit(), confidence, group);
    }

    /** Deterministic Crore conversion - never asked of the model. Null (not zero) if the source value can't be parsed, so a bad read is skipped rather than silently stored as 0. */
    static BigDecimal toCrore(String rawValue, String sourceUnit) {
        if (isBlank(rawValue)) {
            return null;
        }
        BigDecimal value;
        try {
            value = new BigDecimal(rawValue.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
        BigDecimal factor = switch (sourceUnit == null ? "" : sourceUnit.trim().toUpperCase()) {
            case "CRORE" -> BigDecimal.ONE;
            case "MILLION" -> new BigDecimal("0.1");
            case "LAKH" -> new BigDecimal("0.01");
            case "ABSOLUTE" -> new BigDecimal("0.0000001");
            default -> null;
        };
        return factor == null ? null : value.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    private static void addConverted(List<ExtractedFact> facts, String key, String rawValue, String sourceUnit, double confidence, UUID group) {
        BigDecimal converted = toCrore(rawValue, sourceUnit);
        if (converted != null) {
            facts.add(new ExtractedFact(DocumentIntelligenceEngine.normalizeFactType(key), converted.toPlainString(), "", confidence, null, group));
        }
    }

    private static void addIfPresent(List<ExtractedFact> facts, String key, String value, double confidence, UUID group) {
        if (isBlank(value)) {
            return;
        }
        facts.add(new ExtractedFact(DocumentIntelligenceEngine.normalizeFactType(key), value.trim(), "", confidence, null, group));
    }

    private static void addRaw(List<ExtractedFact> facts, String key, String value, double confidence, UUID group) {
        facts.add(new ExtractedFact(DocumentIntelligenceEngine.normalizeFactType(key), value, "", confidence, null, group));
    }

    private static LocalDate parseDate(String isoDate) {
        if (isBlank(isoDate)) {
            return null;
        }
        try {
            return LocalDate.parse(isoDate.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    static String buildPrompt(String documentText) {
        return """
            You are a specialized extractor that understands ONLY a company's own Financial
            Results table - the Sales/Revenue, Profit, EPS, and balance-sheet figures a listed
            company reports to the stock exchange each quarter under SEBI Regulation 33. Ignore
            everything else in the document (board meeting procedural text, auditor's report
            prose, segment-wise breakdowns, unrelated announcements).

            A Financial Results table always presents several period columns side by side (e.g.
            the current quarter, the immediately preceding quarter, the year-ago quarter, and the
            full year). Extract EVERY period column that has real reported figures - this document
            can fill in several quarters of data at once.

            IMPORTANT: only extract a column if it is genuinely QUARTERLY (a single 3-month
            period) or ANNUAL (a full 12-month financial year). SKIP any "Six months ended", "Nine
            months ended", "Half-year ended", or other year-to-date/partial-year aggregate column
            entirely - do not force it into QUARTERLY or ANNUAL, since that would misrepresent
            what period the figures actually cover. These aggregate columns are redundant with the
            quarters that already make them up, not new information.

            If the filing shows both Standalone and Consolidated tables, use the CONSOLIDATED
            figures (the whole corporate group's performance) - use Standalone only if no
            Consolidated table is present.

            For each period column, extract:
            - periodEnd: the period's end date in ISO format (YYYY-MM-DD), e.g. "31-Mar-2026"
              becomes "2026-03-31"
            - periodType: QUARTERLY if this column covers a single quarter, ANNUAL if it covers a
              full financial year (typically labeled "Year Ended")
            - sourceUnit: the unit the table's monetary figures are stated in - CRORE, MILLION,
              LAKH, or ABSOLUTE (plain rupees) - read this from the table header (e.g. "Rs. in
              Crore", "Rs. in million"), it applies to every monetary field in this period
            - sales: "Revenue from Operations" specifically - NEVER "Total Income" even if that
              line is more prominent, since Total Income includes Other Income (interest,
              dividends, one-off gains) which would inflate the operating-sales figure. If a table
              has no separate "Revenue from Operations" line at all (e.g. a bank's Net Interest
              Income model), use whatever single line represents the company's core operating
              income. As a plain number in sourceUnit.
            - pat: Net Profit/(Loss) AFTER TAX attributable to owners of the company - if the
              table separately breaks out "Non-controlling interest" / "Minority Interest" from a
              combined "Profit for the period" figure, use the owners-attributable figure, not the
              combined one (matches how every other financial figure in this system is sourced).
              As a plain number in sourceUnit. REQUIRED if this period is genuinely reported; if
              sales or pat cannot be found for a column, do not include that period at all
            - eps: Basic Earnings Per Share in Rs., as a plain number - NEVER unit-converted,
              already per-share
            - roePercentage, rocePercentage: Return on Equity / Return on Capital Employed as plain
              percentage numbers if explicitly stated - empty if not disclosed (most quarterly
              filings don't state these directly)
            - operatingMarginPercentage, netMarginPercentage: as plain percentage numbers if
              explicitly stated or trivially computable from Operating Profit/Sales or PAT/Total
              Income shown in the SAME table - empty otherwise, do not estimate from unrelated data
            - cashFlowFromOperations, totalAssets, currentAssets, currentLiabilities, totalDebt,
              totalEquity, interestExpense, ebit: plain numbers in sourceUnit if this document's
              balance sheet / cash flow statement / finance-cost line states them - empty if not
              present in this document (many quarterly filings omit the balance sheet)
            - confidence: 0-100, your confidence you read this period's figures correctly

            Use an empty string for any field not stated in the text. Do not invent or estimate
            values that aren't actually shown in the table - leave the field empty instead.

            If this document does not actually contain a financial results table at all (routing
            was wrong), return an empty periods array.

            Document text:
            %s
            """.formatted(documentText);
    }

    static OutputConfig buildOutputConfig() {
        Map<String, Object> periodSchema = Map.ofEntries(
            Map.entry("type", "object"),
            Map.entry("properties", Map.ofEntries(
                Map.entry("periodEnd", Map.of("type", "string")),
                Map.entry("periodType", Map.of("type", "string", "enum", List.of("QUARTERLY", "ANNUAL"))),
                Map.entry("sourceUnit", Map.of("type", "string", "enum", List.of("CRORE", "MILLION", "LAKH", "ABSOLUTE"))),
                Map.entry("sales", Map.of("type", "string")),
                Map.entry("pat", Map.of("type", "string")),
                Map.entry("eps", Map.of("type", "string")),
                Map.entry("roePercentage", Map.of("type", "string")),
                Map.entry("rocePercentage", Map.of("type", "string")),
                Map.entry("operatingMarginPercentage", Map.of("type", "string")),
                Map.entry("netMarginPercentage", Map.of("type", "string")),
                Map.entry("cashFlowFromOperations", Map.of("type", "string")),
                Map.entry("totalAssets", Map.of("type", "string")),
                Map.entry("currentAssets", Map.of("type", "string")),
                Map.entry("currentLiabilities", Map.of("type", "string")),
                Map.entry("totalDebt", Map.of("type", "string")),
                Map.entry("totalEquity", Map.of("type", "string")),
                Map.entry("interestExpense", Map.of("type", "string")),
                Map.entry("ebit", Map.of("type", "string")),
                Map.entry("confidence", Map.of("type", "integer", "description", "0-100 confidence in this period's figures."))
            )),
            Map.entry("required", List.of(
                "periodEnd", "periodType", "sourceUnit", "sales", "pat", "eps", "roePercentage", "rocePercentage",
                "operatingMarginPercentage", "netMarginPercentage", "cashFlowFromOperations", "totalAssets",
                "currentAssets", "currentLiabilities", "totalDebt", "totalEquity", "interestExpense", "ebit", "confidence"
            )),
            Map.entry("additionalProperties", false)
        );
        Map<String, Object> periodsArraySchema = Map.of("type", "array", "items", periodSchema);
        Map<String, Object> rootProperties = Map.of("periods", periodsArraySchema);

        JsonOutputFormat.Schema schema = JsonOutputFormat.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty("properties", JsonValue.from(rootProperties))
            .putAdditionalProperty("required", JsonValue.from(List.of("periods")))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .build();

        return OutputConfig.builder()
            .format(JsonOutputFormat.builder().schema(schema).build())
            .build();
    }
}
