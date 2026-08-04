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
 * Stage 2: knows only about which companies a news item materially affects and how - nothing
 * else (no order details, no forward-looking guidance). Unlike {@link OrderExtractor}'s
 * at-most-one-order assumption, a single news item (a government policy announcement, an
 * industry-wide development) can name several companies at once, so each becomes its own
 * {@code fact_group} - same pattern {@link ManagementExtractor} established for multiple
 * statements per document.
 *
 * <p>Deliberately does NOT know AlphaGraph's tracked instrument universe - it names companies
 * freely, in its own words, exactly as the text does. Resolving a name against
 * {@code reference.instruments} (and dropping the ones that don't match) is
 * {@code corporate.news.NewsInstrumentMatcher}'s job, a separate, deterministic step. Keeping
 * extraction and universe-matching apart means this extractor's output stays valid even if the
 * tracked universe changes later - no re-extraction needed.
 */
@Component
class NewsExtractor implements DocumentExtractor {

    private static final Model MODEL = Model.CLAUDE_SONNET_5;
    private static final long MAX_TOKENS = 2048L;

    private final AnthropicClient client;
    private final ObjectMapper objectMapper;

    NewsExtractor(AnthropicClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(DocumentClassification classification) {
        return classification.recommendedExtractors().stream()
            .anyMatch(name -> name.equalsIgnoreCase("NEWS"));
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
            throw new IllegalStateException("Claude API rate limit hit during news extraction: " + e.getMessage(), e);
        } catch (AnthropicIoException e) {
            throw new IllegalStateException("Network failure calling Claude API: " + e.getMessage(), e);
        } catch (AnthropicServiceException e) {
            throw new IllegalStateException("Claude API call failed: " + e.getMessage(), e);
        }
    }

    ExtractionResult parseResult(String rawJson) {
        LlmNewsExtractionResponse response;
        try {
            response = objectMapper.readValue(rawJson, LlmNewsExtractionResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse Claude's structured-output JSON: " + rawJson, e);
        }

        List<ExtractedFact> facts = new ArrayList<>();
        for (LlmNewsCompanyImpact impact : response.impacts()) {
            if (isBlank(impact.companyName()) || isBlank(impact.direction())) {
                continue;
            }
            UUID group = UUID.randomUUID();
            double confidence = impact.confidence();

            facts.add(new ExtractedFact(
                DocumentIntelligenceEngine.normalizeFactType("companyName"), impact.companyName().trim(), "", confidence, null, group
            ));
            facts.add(new ExtractedFact(
                DocumentIntelligenceEngine.normalizeFactType("direction"), impact.direction().trim(), "", confidence, null, group
            ));
            addIfPresent(facts, "signal", impact.signal(), confidence, group);
            addIfPresent(facts, "impactSummary", impact.impactSummary(), confidence, group);
        }

        return new ExtractionResult(facts);
    }

    private static void addIfPresent(List<ExtractedFact> facts, String key, String value, double confidence, UUID group) {
        if (isBlank(value)) {
            return;
        }
        facts.add(new ExtractedFact(DocumentIntelligenceEngine.normalizeFactType(key), value.trim(), "", confidence, null, group));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    static String buildPrompt(String documentText) {
        return """
            You are a specialized extractor that identifies which companies a news item
            materially affects and how - nothing else. This is general news (business news,
            government/policy notifications, industry or sector developments), NOT a company's
            own filing, so there may be zero, one, or several affected companies named or clearly
            implied by the text (e.g. a government scheme naming an industry rather than a single
            company - identify the specific companies that industry news would plausibly affect,
            if the text names or clearly implies them).

            For EVERY company the news materially affects, extract:
            - companyName: the company's name exactly as it appears (or is clearly implied) in
              the text - do not abbreviate or normalize it yourself
            - direction: POSITIVE, NEGATIVE, or NEUTRAL - is this news good, bad, or neutral for
              that company's investment thesis
            - signal: a short 2-4 word descriptive label for the catalyst, e.g. "PLI Beneficiary",
              "Regulatory Headwind", "New Market Access"
            - impactSummary: one sentence explaining specifically how this news affects this
              company
            - confidence: 0-100, your confidence in this company being genuinely, materially
              affected (not just tangentially mentioned)

            If the news doesn't materially affect any identifiable company, return an empty
            impacts list. Do not invent companies that aren't named or clearly implied by the
            text, and do not include a company that's only mentioned in passing with no real
            impact.

            Document text:
            %s
            """.formatted(documentText);
    }

    static OutputConfig buildOutputConfig() {
        Map<String, Object> impactSchema = Map.of(
            "type", "object",
            "properties", Map.of(
                "companyName", Map.of("type", "string"),
                "direction", Map.of("type", "string", "enum", List.of("POSITIVE", "NEGATIVE", "NEUTRAL")),
                "signal", Map.of("type", "string"),
                "impactSummary", Map.of("type", "string"),
                "confidence", Map.of("type", "integer", "description", "0-100 confidence in this extraction.")
            ),
            "required", List.of("companyName", "direction", "signal", "impactSummary", "confidence"),
            "additionalProperties", false
        );
        Map<String, Object> impactsArraySchema = Map.of("type", "array", "items", impactSchema);
        Map<String, Object> rootProperties = Map.of("impacts", impactsArraySchema);

        JsonOutputFormat.Schema schema = JsonOutputFormat.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty("properties", JsonValue.from(rootProperties))
            .putAdditionalProperty("required", JsonValue.from(List.of("impacts")))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .build();

        return OutputConfig.builder()
            .format(JsonOutputFormat.builder().schema(schema).build())
            .build();
    }
}
