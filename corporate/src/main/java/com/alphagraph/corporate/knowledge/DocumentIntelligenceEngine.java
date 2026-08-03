package com.alphagraph.corporate.knowledge;

import com.alphagraph.corporate.api.Sentiment;
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

import java.util.List;
import java.util.Map;

/**
 * Runs ONE canonical Claude API call per document, extracting everything downstream engines need
 * in a single pass: a document-level summary/type/sentiment, open-ended topic tags, and a bag of
 * business facts (key-value-unit triples). This replaces the original Module 2.3 design where
 * {@code CorporateEventEngine} called Claude directly - under the retrofitted architecture, this
 * is the ONLY class in the corporate module that calls Claude; every rule engine downstream
 * (Corporate Event Engine, Order Book Engine, future Management Commentary/News engines) reads
 * this engine's stored output instead of re-reading the PDF text or re-calling the LLM itself.
 *
 * <p>Uses the same "raw" structured-outputs path as the original Module 2.3 engine (a hand-built
 * {@link JsonOutputFormat.Schema}) rather than deriving the schema from a Java class, so every
 * field can carry its own model-facing description.
 */
@Component
public class DocumentIntelligenceEngine {

    /** Bumped whenever the canonical extraction prompt/schema changes meaningfully - carried onto every {@link com.alphagraph.corporate.api.DocumentSummary}. */
    static final int PROMPT_VERSION = 1;

    // Sonnet 5, matching Module 2.3's cost/quality call - canonical extraction is still a bounded
    // structured-extraction task (fixed field set, enumerable sentiment/topics), not open-ended
    // reasoning.
    private static final Model MODEL = Model.CLAUDE_SONNET_5;
    private static final long MAX_TOKENS = 8192L;

    private final AnthropicClient client;
    private final ObjectMapper objectMapper;

    public DocumentIntelligenceEngine(AnthropicClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    /** Returns the canonical extraction for one document's already-extracted text. Never re-parses a PDF. */
    public CanonicalExtraction extract(String documentText) {
        MessageCreateParams createParams = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(MAX_TOKENS)
            .outputConfig(buildOutputConfig())
            .addUserMessage(buildPrompt(documentText))
            .build();

        String rawJson = callClaude(createParams);
        return parseExtraction(rawJson);
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
            throw new IllegalStateException("Claude API rate limit hit during knowledge extraction: " + e.getMessage(), e);
        } catch (AnthropicIoException e) {
            throw new IllegalStateException("Network failure calling Claude API: " + e.getMessage(), e);
        } catch (AnthropicServiceException e) {
            throw new IllegalStateException("Claude API call failed: " + e.getMessage(), e);
        }
    }

    CanonicalExtraction parseExtraction(String rawJson) {
        LlmCanonicalResponse response;
        try {
            response = objectMapper.readValue(rawJson, LlmCanonicalResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse Claude's structured-output JSON: " + rawJson, e);
        }

        Sentiment sentiment;
        try {
            sentiment = Sentiment.valueOf(response.sentiment());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Claude returned a sentiment outside the declared schema enum: " + response.sentiment(), e);
        }

        if (response.confidence() < 0 || response.confidence() > 100) {
            throw new IllegalStateException("Claude returned an out-of-range confidence (" + response.confidence() + ")");
        }

        List<ExtractedFact> facts = response.facts().stream()
            .map(f -> new ExtractedFact(normalizeFactType(f.key()), f.value(), f.unit(), response.confidence()))
            .toList();

        return new CanonicalExtraction(
            response.documentType(), sentiment, response.confidence(), response.summary(),
            response.topics(), facts, rawJson
        );
    }

    /**
     * Lowercased, non-alphanumeric-stripped, so a downstream lookup by a known key ("orderValue")
     * survives minor LLM key-naming variance ("Order Value", "order_value"). Public - every
     * downstream reader (Corporate Event Engine, Order Book Engine) normalizes its own lookup
     * keys with this exact same function, so a fact written here is always found there.
     */
    public static String normalizeFactType(String rawKey) {
        return rawKey.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    static String buildPrompt(String documentText) {
        return """
            You are analyzing one corporate document (an exchange announcement, quarterly result,
            investor presentation, or similar filing from an Indian listed company) to extract
            structured, reusable knowledge for several downstream analysis engines - not just one
            specific use case.

            Produce:
            - documentType: a short label for what kind of document this is (e.g.
              "ORDER_ANNOUNCEMENT", "CAPACITY_EXPANSION", "FINANCIAL_RESULT",
              "MANAGEMENT_COMMENTARY", "CORPORATE_ACTION", "GENERAL_UPDATE") - use your judgment,
              this is not a fixed list
            - sentiment: POSITIVE, NEGATIVE, or NEUTRAL - the document's overall tone
            - confidence: 0-100, your confidence in this overall extraction
            - summary: one or two sentences restating what the document says
            - topics: open-ended tags describing what the document is about (e.g. "Defence",
              "Radar"). In addition, whenever the document genuinely describes one of these named
              corporate event categories, include that EXACT category name as one of the topics
              (verbatim, so downstream rule engines can match on it reliably) - do not include one
              that doesn't apply:
                Large Order, Capacity Expansion, New Plant, Acquisition, Merger, Joint Venture,
                PLI Approval, Patent, Export Approval, Government Contract, Debt Raising,
                Promoter Buying, Promoter Selling
            - facts: every concrete business fact in the document, as key-value-unit triples.
              If the document describes an order, tender win, execution update, cancellation, or
              completion, use these exact keys where applicable so downstream engines can find
              them reliably:
                - customer: the counterparty name
                - orderValue: the numeric order value (unit: CRORE, LAKH, or ABSOLUTE)
                - businessUnit: the business unit or product line
                - executionStart: when execution begins (year or date)
                - executionEnd: when execution is expected to complete (year or date)
                - orderScope: DOMESTIC or EXPORT
                - orderSector: GOVERNMENT or PRIVATE
                - orderRecurrence: RECURRING or ONE_TIME
                - orderLifecycleStage: NEW_ORDER, TENDER_WIN, EXECUTION_UPDATE, CANCELLATION, or COMPLETION
              For any other kind of fact, use a clear, descriptive key of your own choosing. Use
              an empty string for unit when no unit applies. If the document contains no
              extractable facts, return an empty facts list - do not invent facts that aren't
              actually in the text.

            Document text:
            %s
            """.formatted(documentText);
    }

    static OutputConfig buildOutputConfig() {
        Map<String, Object> factSchema = Map.of(
            "type", "object",
            "properties", Map.of(
                "key", Map.of("type", "string", "description", "The fact's identifier, e.g. 'customer', 'orderValue', 'businessUnit'."),
                "value", Map.of("type", "string", "description", "The fact's value as text."),
                "unit", Map.of("type", "string", "description", "Unit for the value (e.g. 'CRORE'), or an empty string if not applicable.")
            ),
            "required", List.of("key", "value", "unit"),
            "additionalProperties", false
        );
        Map<String, Object> factsArraySchema = Map.of("type", "array", "items", factSchema);
        Map<String, Object> topicsArraySchema = Map.of("type", "array", "items", Map.of("type", "string"));

        Map<String, Object> rootProperties = Map.of(
            "documentType", Map.of("type", "string", "description", "What kind of document this is."),
            "sentiment", Map.of("type", "string", "enum", List.of("POSITIVE", "NEGATIVE", "NEUTRAL")),
            "confidence", Map.of("type", "integer", "description", "0-100 confidence in this extraction."),
            "summary", Map.of("type", "string", "description", "One or two sentence summary."),
            "topics", topicsArraySchema,
            "facts", factsArraySchema
        );

        JsonOutputFormat.Schema schema = JsonOutputFormat.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty("properties", JsonValue.from(rootProperties))
            .putAdditionalProperty("required", JsonValue.from(List.of("documentType", "sentiment", "confidence", "summary", "topics", "facts")))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .build();

        return OutputConfig.builder()
            .format(JsonOutputFormat.builder().schema(schema).build())
            .build();
    }
}
