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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Stage 2: knows only about orders - value, customer, business unit, execution timeline,
 * domestic/export, government/private, recurring/one-time, and lifecycle stage (new order, tender
 * win, execution update, cancellation, completion). Nothing else. Migrated out of Stage 1's
 * originally shared prompt (Module 2.4's first design) once a second domain (Management
 * Commentary, Module 2.5) needed its own specialized extraction - a shared prompt covering both
 * domains would dilute both.
 *
 * <p>Normalizes into the exact same {@code document_facts} key vocabulary the original shared
 * prompt used (customer, ordervalue, businessunit, executionstart, executionend, orderscope,
 * ordersector, orderrecurrence, orderlifecyclestage), so {@code corporate.orderbook.
 * OrderBookLedgerParser} needed zero changes for this migration - it has always read canonical
 * facts, never cared which extractor wrote them.
 *
 * <p>Asks for an execution start year plus a duration in months (not two independently-guessed
 * years) and computes the end year deterministically in Java - more reliable than hoping the
 * model's two independently-stated years are self-consistent.
 */
@Component
class OrderExtractor implements DocumentExtractor {

    private static final Model MODEL = Model.CLAUDE_SONNET_5;
    private static final long MAX_TOKENS = 2048L;

    private final AnthropicClient client;
    private final ObjectMapper objectMapper;

    OrderExtractor(AnthropicClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(DocumentClassification classification) {
        return classification.recommendedExtractors().stream()
            .anyMatch(name -> name.equalsIgnoreCase("ORDER"));
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
            throw new IllegalStateException("Claude API rate limit hit during order extraction: " + e.getMessage(), e);
        } catch (AnthropicIoException e) {
            throw new IllegalStateException("Network failure calling Claude API: " + e.getMessage(), e);
        } catch (AnthropicServiceException e) {
            throw new IllegalStateException("Claude API call failed: " + e.getMessage(), e);
        }
    }

    ExtractionResult parseResult(String rawJson) {
        LlmOrderExtractionResponse response;
        try {
            response = objectMapper.readValue(rawJson, LlmOrderExtractionResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse Claude's structured-output JSON: " + rawJson, e);
        }

        // Stage 1's routing hint isn't infallible - if Stage 2 finds this document isn't actually
        // order-related after all, orderLifecycleStage comes back blank and no facts are emitted.
        if (isBlank(response.orderLifecycleStage())) {
            return new ExtractionResult(List.of());
        }

        double confidence = response.confidence();
        List<ExtractedFact> facts = new ArrayList<>();
        addIfPresent(facts, "customer", response.customer(), "", confidence);
        addIfPresent(facts, "orderValue", response.orderValue(), response.currency(), confidence);
        addIfPresent(facts, "businessUnit", response.businessUnit(), "", confidence);
        addIfPresent(facts, "executionStart", response.executionStartYear(), "", confidence);
        addIfPresent(facts, "orderScope", response.orderScope(), "", confidence);
        addIfPresent(facts, "orderSector", response.orderSector(), "", confidence);
        addIfPresent(facts, "orderRecurrence", response.orderRecurrence(), "", confidence);
        addIfPresent(facts, "orderLifecycleStage", response.orderLifecycleStage(), "", confidence);

        String executionEnd = computeExecutionEndYear(response.executionStartYear(), response.executionMonths());
        addIfPresent(facts, "executionEnd", executionEnd, "", confidence);

        return new ExtractionResult(facts);
    }

    /** Start year + duration in months, computed deterministically rather than asked of the model as two independent years. */
    private static String computeExecutionEndYear(String startYear, String months) {
        if (isBlank(startYear) || isBlank(months)) {
            return null;
        }
        try {
            int start = Integer.parseInt(startYear.trim());
            int durationMonths = Integer.parseInt(months.trim());
            int endYear = start + (int) Math.ceil(durationMonths / 12.0);
            return String.valueOf(endYear);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void addIfPresent(List<ExtractedFact> facts, String key, String value, String unit, double confidence) {
        if (isBlank(value)) {
            return;
        }
        facts.add(new ExtractedFact(DocumentIntelligenceEngine.normalizeFactType(key), value.trim(), unit == null ? "" : unit, confidence));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    static String buildPrompt(String documentText) {
        return """
            You are a specialized extractor that understands ONLY corporate orders - new orders,
            tender wins, order execution updates, order cancellations, and order completions.
            Ignore everything else in the document that isn't about an order.

            Extract, if present in the text:
            - customer: the counterparty who placed or awarded the order
            - orderValue: the numeric order value
            - currency: the unit for orderValue - CRORE, LAKH, or ABSOLUTE (plain rupees)
            - businessUnit: the business unit, product line, or segment the order falls under
            - executionStartYear: the year execution begins
            - executionMonths: the execution/contract duration in months
            - orderScope: DOMESTIC or EXPORT
            - orderSector: GOVERNMENT or PRIVATE
            - orderRecurrence: RECURRING or ONE_TIME
            - orderLifecycleStage: NEW_ORDER, TENDER_WIN, EXECUTION_UPDATE, CANCELLATION, or
              COMPLETION - REQUIRED if this document genuinely describes an order. If, on closer
              reading, this document does NOT actually describe an order at all, leave this field
              as an empty string and leave every other field empty too.

            Use an empty string for any field not stated in the text. Do not invent values.

            Document text:
            %s
            """.formatted(documentText);
    }

    static OutputConfig buildOutputConfig() {
        // 11 entries exceeds Map.of()'s 10-pair overload, so this uses Map.ofEntries() instead.
        Map<String, Object> rootProperties = Map.ofEntries(
            Map.entry("customer", Map.of("type", "string")),
            Map.entry("orderValue", Map.of("type", "string")),
            Map.entry("currency", Map.of("type", "string", "enum", List.of("CRORE", "LAKH", "ABSOLUTE", ""))),
            Map.entry("businessUnit", Map.of("type", "string")),
            Map.entry("executionStartYear", Map.of("type", "string")),
            Map.entry("executionMonths", Map.of("type", "string")),
            Map.entry("orderScope", Map.of("type", "string", "enum", List.of("DOMESTIC", "EXPORT", ""))),
            Map.entry("orderSector", Map.of("type", "string", "enum", List.of("GOVERNMENT", "PRIVATE", ""))),
            Map.entry("orderRecurrence", Map.of("type", "string", "enum", List.of("RECURRING", "ONE_TIME", ""))),
            Map.entry("orderLifecycleStage", Map.of("type", "string", "enum", List.of(
                "NEW_ORDER", "TENDER_WIN", "EXECUTION_UPDATE", "CANCELLATION", "COMPLETION", ""
            ))),
            Map.entry("confidence", Map.of("type", "integer", "description", "0-100 confidence in this extraction."))
        );

        JsonOutputFormat.Schema schema = JsonOutputFormat.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty("properties", JsonValue.from(rootProperties))
            .putAdditionalProperty("required", JsonValue.from(List.of(
                "customer", "orderValue", "currency", "businessUnit", "executionStartYear", "executionMonths",
                "orderScope", "orderSector", "orderRecurrence", "orderLifecycleStage", "confidence"
            )))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .build();

        return OutputConfig.builder()
            .format(JsonOutputFormat.builder().schema(schema).build())
            .build();
    }
}
