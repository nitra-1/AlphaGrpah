package com.alphagraph.corporate.events;

import com.alphagraph.corporate.api.EventSignal;
import com.alphagraph.corporate.api.EventType;
import com.alphagraph.corporate.api.RevenueImpact;
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
 * Classifies one document's already-extracted text into zero or more of the 13 named corporate
 * event types, via a real Claude API call (docs/claude.md Phase 2 Module 2.3 - the user explicitly
 * chose "Real LLM call (Claude API)" over rule-based keyword matching or a hybrid, as a genuine
 * architectural decision with real trade-offs). Deliberately does NOT implement
 * {@code common.engine.Engine<I, O extends Score>}: that contract is built around exactly one
 * deterministic {@link com.alphagraph.common.engine.Score} per input, resolved via a
 * {@code RuleSet} (docs/002_Engine_Architecture.md §5) - this engine produces zero-or-more
 * {@link ExtractedEvent}s per document from genuine semantic classification, not threshold rules,
 * so forcing it into the Score/RuleSet shape would misrepresent what it actually does.
 *
 * <p>Uses the SDK's "raw" structured-outputs path (a hand-built {@link JsonOutputFormat.Schema})
 * rather than deriving the schema from a Java class via reflection, so every field can carry its
 * own model-facing description - useful prompt-engineering surface a reflection-derived schema
 * would not give, and avoids depending on unverified enum-schema reflection behavior.
 */
@Component
public class CorporateEventEngine {

    /**
     * Bumped whenever the classification prompt or schema changes meaningfully - carried onto
     * every {@link com.alphagraph.corporate.api.CorporateEvent#promptVersion()}, playing the same
     * reproducibility-tracking role {@code ruleSetVersion} plays on every Phase 1 engine's Score.
     */
    static final int PROMPT_VERSION = 1;

    // Sonnet 5, not Opus 5 - a genuine cost/quality trade-off the user made explicitly. This is a
    // bounded classification task (13 fixed categories, schema enforced server-side via structured
    // outputs regardless of model) rather than open-ended reasoning, so a mid-tier model performs
    // close to frontier-tier here at ~40% lower cost than Opus 5.
    private static final Model MODEL = Model.CLAUDE_SONNET_5;
    private static final long MAX_TOKENS = 8192L;

    private final AnthropicClient client;
    private final ObjectMapper objectMapper;

    /**
     * {@code client} is injected (see {@link AnthropicClientConfig}) rather than constructed here
     * directly - the SDK's zero-arg {@code AnthropicOkHttpClient.fromEnv()} throws if
     * {@code ANTHROPIC_API_KEY} isn't set, which would make this class impossible to unit-test
     * without live credentials if the constructor called it internally.
     */
    public CorporateEventEngine(AnthropicClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns zero or more events detected in {@code documentText}. Never re-parses a PDF - the
     * caller supplies text the Document Pipeline (Module 2.1) already extracted.
     */
    public List<ExtractedEvent> extractEvents(String documentText) {
        MessageCreateParams createParams = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(MAX_TOKENS)
            .outputConfig(buildOutputConfig())
            .addUserMessage(buildPrompt(documentText))
            .build();

        String rawJson = callClaude(createParams);
        return parseEvents(rawJson);
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
            throw new IllegalStateException("Claude API rate limit hit during event extraction: " + e.getMessage(), e);
        } catch (AnthropicIoException e) {
            throw new IllegalStateException("Network failure calling Claude API: " + e.getMessage(), e);
        } catch (AnthropicServiceException e) {
            throw new IllegalStateException("Claude API call failed: " + e.getMessage(), e);
        }
    }

    List<ExtractedEvent> parseEvents(String rawJson) {
        LlmEventResponse response;
        try {
            response = objectMapper.readValue(rawJson, LlmEventResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse Claude's structured-output JSON: " + rawJson, e);
        }

        List<ExtractedEvent> events = new ArrayList<>();
        for (LlmEvent event : response.events()) {
            events.add(toExtractedEvent(event, rawJson));
        }
        return events;
    }

    ExtractedEvent toExtractedEvent(LlmEvent event, String rawJson) {
        EventType eventType;
        RevenueImpact revenueImpact;
        EventSignal signal;
        try {
            eventType = EventType.valueOf(event.eventType());
            revenueImpact = RevenueImpact.valueOf(event.revenueImpact());
            signal = EventSignal.valueOf(event.signal());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Claude returned a value outside the declared schema enum: " + event, e);
        }

        if (event.confidence() < 0 || event.confidence() > 100) {
            throw new IllegalStateException("Claude returned an out-of-range confidence (" + event.confidence() + "): " + event);
        }

        return new ExtractedEvent(
            eventType, event.category(), event.summary(), event.confidence(),
            event.expectedDuration(), revenueImpact, signal, rawJson
        );
    }

    static String buildPrompt(String documentText) {
        return """
            You are analyzing one corporate document (an exchange announcement, quarterly result, \
            investor presentation, or similar filing from an Indian listed company) to detect \
            corporate events relevant to an equity research analyst.

            Identify every event in the document that matches one of these 13 categories: \
            Large Order, Capacity Expansion, New Plant, Acquisition, Merger, Joint Venture, \
            PLI Approval, Patent, Export Approval, Government Contract, Debt Raising, \
            Promoter Buying, Promoter Selling.

            For each matching event, report:
            - eventType: exactly one of the 13 category codes above
            - category: a short 1-3 word label for the event's financial nature (e.g. "Revenue \
              Positive", "Financing", "Ownership Signal")
            - summary: one sentence restating what happened (e.g. "BEL received a Rs 2,800 Cr order.")
            - confidence: 0-100, how confident you are this event is correctly classified
            - expectedDuration: how long the event's effect is expected to last (e.g. "3 Years", \
              "One-time", "Ongoing")
            - revenueImpact: HIGH, MEDIUM, LOW, or NONE
            - signal: POSITIVE, NEGATIVE, or NEUTRAL - the overall investment-thesis direction

            If the document describes no event matching any of the 13 categories, return an empty \
            events list. Do not invent an event that is not actually described in the text.

            Document text:
            %s
            """.formatted(documentText);
    }

    static OutputConfig buildOutputConfig() {
        Map<String, Object> eventTypeProperty = Map.of(
            "type", "string",
            "enum", List.of(
                "LARGE_ORDER", "CAPACITY_EXPANSION", "NEW_PLANT", "ACQUISITION", "MERGER", "JOINT_VENTURE",
                "PLI_APPROVAL", "PATENT", "EXPORT_APPROVAL", "GOVERNMENT_CONTRACT", "DEBT_RAISING",
                "PROMOTER_BUYING", "PROMOTER_SELLING"
            ),
            "description", "Exactly one of the 13 named corporate event categories."
        );
        Map<String, Object> categoryProperty = Map.of(
            "type", "string",
            "description", "A short 1-3 word label for the event's financial nature, e.g. 'Revenue Positive', 'Financing', 'Ownership Signal'."
        );
        Map<String, Object> summaryProperty = Map.of(
            "type", "string",
            "description", "One sentence restating what happened, e.g. 'BEL received a Rs 2,800 Cr order.'"
        );
        Map<String, Object> confidenceProperty = Map.of(
            "type", "integer",
            "description", "Confidence this classification is correct, 0-100."
        );
        Map<String, Object> expectedDurationProperty = Map.of(
            "type", "string",
            "description", "How long the event's effect is expected to last, e.g. '3 Years', 'One-time', 'Ongoing'."
        );
        Map<String, Object> revenueImpactProperty = Map.of(
            "type", "string",
            "enum", List.of("HIGH", "MEDIUM", "LOW", "NONE"),
            "description", "How materially this event is expected to affect revenue."
        );
        Map<String, Object> signalProperty = Map.of(
            "type", "string",
            "enum", List.of("POSITIVE", "NEGATIVE", "NEUTRAL"),
            "description", "The overall investment-thesis direction of this event."
        );

        Map<String, Object> eventProperties = Map.of(
            "eventType", eventTypeProperty,
            "category", categoryProperty,
            "summary", summaryProperty,
            "confidence", confidenceProperty,
            "expectedDuration", expectedDurationProperty,
            "revenueImpact", revenueImpactProperty,
            "signal", signalProperty
        );
        Map<String, Object> eventSchema = Map.of(
            "type", "object",
            "properties", eventProperties,
            "required", List.of("eventType", "category", "summary", "confidence", "expectedDuration", "revenueImpact", "signal"),
            "additionalProperties", false
        );
        Map<String, Object> eventsArraySchema = Map.of(
            "type", "array",
            "items", eventSchema
        );

        JsonOutputFormat.Schema schema = JsonOutputFormat.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty("properties", JsonValue.from(Map.of("events", eventsArraySchema)))
            .putAdditionalProperty("required", JsonValue.from(List.of("events")))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .build();

        return OutputConfig.builder()
            .format(JsonOutputFormat.builder().schema(schema).build())
            .build();
    }
}
