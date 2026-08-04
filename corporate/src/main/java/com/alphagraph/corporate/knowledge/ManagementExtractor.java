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
import java.util.UUID;

/**
 * Stage 2: knows only about forward-looking management commentary - revenue guidance, margin
 * guidance, capex, demand, pricing, competition, hiring, exports, and risk commentary. Nothing
 * else (no order details, no corporate-action classification). A single document can contain
 * several distinct statements at once, unlike {@link OrderExtractor}'s at-most-one-order
 * assumption - each becomes its own {@code fact_group} so a downstream reader
 * ({@code corporate.commentary.ManagementObservationParser}) can reassemble each statement's
 * facts without conflating two different statements from the same document.
 */
@Component
class ManagementExtractor implements DocumentExtractor {

    private static final Model MODEL = Model.CLAUDE_SONNET_5;
    private static final long MAX_TOKENS = 4096L;

    private final AnthropicClient client;
    private final ObjectMapper objectMapper;

    ManagementExtractor(AnthropicClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(DocumentClassification classification) {
        return classification.recommendedExtractors().stream()
            .anyMatch(name -> name.equalsIgnoreCase("MANAGEMENT"));
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
            throw new IllegalStateException("Claude API rate limit hit during management extraction: " + e.getMessage(), e);
        } catch (AnthropicIoException e) {
            throw new IllegalStateException("Network failure calling Claude API: " + e.getMessage(), e);
        } catch (AnthropicServiceException e) {
            throw new IllegalStateException("Claude API call failed: " + e.getMessage(), e);
        }
    }

    ExtractionResult parseResult(String rawJson) {
        LlmManagementExtractionResponse response;
        try {
            response = objectMapper.readValue(rawJson, LlmManagementExtractionResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse Claude's structured-output JSON: " + rawJson, e);
        }

        List<ExtractedFact> facts = new ArrayList<>();
        for (LlmManagementStatement statement : response.statements()) {
            if (isBlank(statement.metricType())) {
                continue;
            }
            UUID group = UUID.randomUUID();
            double confidence = statement.confidence();
            String commitment = normalizeCommitment(statement.commitmentLevel());

            // commitmentLevel lives on this group's primary fact (metrictype) by convention -
            // every fact in a group shares the same statement, so it would be redundant to repeat
            // it on every one.
            facts.add(new ExtractedFact(
                DocumentIntelligenceEngine.normalizeFactType("metricType"), statement.metricType(), "", confidence, commitment, group
            ));
            addIfPresent(facts, "guidanceValue", statement.valueText(), confidence, group);
            addIfPresent(facts, "guidanceValueNumeric", statement.valueNumeric(), confidence, group);
            addIfPresent(facts, "guidancePeriod", statement.period(), confidence, group);
            addIfPresent(facts, "direction", statement.direction(), confidence, group);
            addIfPresent(facts, "signal", statement.signal(), confidence, group);
        }

        return new ExtractionResult(facts);
    }

    private static void addIfPresent(List<ExtractedFact> facts, String key, String value, double confidence, UUID group) {
        if (isBlank(value)) {
            return;
        }
        facts.add(new ExtractedFact(DocumentIntelligenceEngine.normalizeFactType(key), value.trim(), "", confidence, null, group));
    }

    private static String normalizeCommitment(String raw) {
        return isBlank(raw) ? null : raw.trim().toUpperCase();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    static String buildPrompt(String documentText) {
        return """
            You are a specialized extractor that understands ONLY forward-looking management
            commentary - guidance, outlook, and expectations that management states about the
            company's future. Ignore backward-looking facts (an order already won, a result
            already reported) unless management is using them to frame forward guidance.

            Find every distinct forward-looking statement in the document. For each one, extract:
            - metricType: exactly one of REVENUE_GUIDANCE, MARGIN_GUIDANCE, CAPEX, DEMAND,
              PRICING, COMPETITION, HIRING, EXPORTS, RISK - whichever best fits
            - valueText: the statement's value or description as stated, e.g. "30%%", "improving by
              100 basis points", "strong domestic demand", "increased competitive intensity in
              exports"
            - valueNumeric: if valueText is a plain percentage or number, the bare number (e.g.
              "30" for "30%%"); leave empty if the statement is qualitative prose with no number to
              extract - do not force a number that isn't there
            - period: the time period the statement covers, e.g. "next two years", "FY27", "Q2
              FY27" - empty if not stated
            - direction: POSITIVE, NEGATIVE, or NEUTRAL - is this statement good, bad, or neutral
              for the investment thesis
            - signal: a short 2-4 word descriptive label for what this statement signals, e.g.
              "Growth Visibility", "Margin Pressure", "Export Headwinds", "Demand Strength"
            - commitmentLevel: how strongly management is committing to this, based on the actual
              language used - LOW for hedged language ("we hope", "we aim to"), MEDIUM for plain
              expectation ("we expect", "we anticipate"), HIGH for confident assertion ("we are
              confident", "we will achieve"), VERY_HIGH for statements backed by concrete already-
              secured facts ("orders already secured", "contracts already signed")
            - confidence: 0-100, your confidence you extracted and classified this statement
              correctly

            If the document contains no forward-looking management commentary, return an empty
            statements list. Do not invent statements that aren't actually in the text.

            Document text:
            %s
            """.formatted(documentText);
    }

    static OutputConfig buildOutputConfig() {
        Map<String, Object> statementSchema = Map.ofEntries(
            Map.entry("type", "object"),
            Map.entry("properties", Map.ofEntries(
                Map.entry("metricType", Map.of("type", "string", "enum", List.of(
                    "REVENUE_GUIDANCE", "MARGIN_GUIDANCE", "CAPEX", "DEMAND", "PRICING",
                    "COMPETITION", "HIRING", "EXPORTS", "RISK"
                ))),
                Map.entry("valueText", Map.of("type", "string")),
                Map.entry("valueNumeric", Map.of("type", "string", "description", "Bare number if valueText is a plain figure, empty string otherwise.")),
                Map.entry("period", Map.of("type", "string")),
                Map.entry("direction", Map.of("type", "string", "enum", List.of("POSITIVE", "NEGATIVE", "NEUTRAL"))),
                Map.entry("signal", Map.of("type", "string")),
                Map.entry("commitmentLevel", Map.of("type", "string", "enum", List.of("LOW", "MEDIUM", "HIGH", "VERY_HIGH"))),
                Map.entry("confidence", Map.of("type", "integer", "description", "0-100 confidence in this extraction."))
            )),
            Map.entry("required", List.of(
                "metricType", "valueText", "valueNumeric", "period", "direction", "signal", "commitmentLevel", "confidence"
            )),
            Map.entry("additionalProperties", false)
        );
        Map<String, Object> statementsArraySchema = Map.of("type", "array", "items", statementSchema);
        Map<String, Object> rootProperties = Map.of("statements", statementsArraySchema);

        JsonOutputFormat.Schema schema = JsonOutputFormat.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty("properties", JsonValue.from(rootProperties))
            .putAdditionalProperty("required", JsonValue.from(List.of("statements")))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .build();

        return OutputConfig.builder()
            .format(JsonOutputFormat.builder().schema(schema).build())
            .build();
    }
}
