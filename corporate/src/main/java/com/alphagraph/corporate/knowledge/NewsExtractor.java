package com.alphagraph.corporate.knowledge;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
 *
 * <p>Module 2.7: also identifies, per impact, which OTHER entity a company's relationship runs
 * through - a government scheme it benefits from, a theme it belongs to, a competitor, a
 * customer - and the relationship type connecting them (a controlled vocabulary, matching
 * {@code corporate.api.RelationshipType}). This is optional and independent of the tracked-
 * universe question above: {@code corporate.relationships.RelationshipBuilder} resolves BOTH the
 * company and the related entity through {@code corporate.relationships.EntityResolver} against
 * the full graph (not just tracked instruments), so an untracked company like Kaynes still gets a
 * real BENEFICIARY_OF edge even though it never gets a {@code document_instrument_links} row.
 */
@Component
class NewsExtractor implements DocumentExtractor {

    private static final Logger log = LoggerFactory.getLogger(NewsExtractor.class);

    // Package-private, not private: ClaudeNewsExtractionClient builds its own MessageCreateParams
    // from these, same model/token budget this class always used before the Gemini pilot split
    // provider-calling logic out into ClaudeNewsExtractionClient/GeminiNewsExtractionClient.
    static final Model MODEL = Model.CLAUDE_SONNET_5;
    static final long MAX_TOKENS = 2048L;

    private final ClaudeNewsExtractionClient claudeClient;
    private final GeminiNewsExtractionClient geminiClient;
    private final ObjectMapper objectMapper;
    private final boolean useGemini;

    NewsExtractor(
        ClaudeNewsExtractionClient claudeClient, GeminiNewsExtractionClient geminiClient, ObjectMapper objectMapper,
        @Value("${alphagraph.corporate.news-extractor.use-gemini:true}") boolean useGemini
    ) {
        this.claudeClient = claudeClient;
        this.geminiClient = geminiClient;
        this.objectMapper = objectMapper;
        this.useGemini = useGemini;
    }

    @Override
    public boolean supports(DocumentClassification classification) {
        return classification.recommendedExtractors().stream()
            .anyMatch(name -> name.equalsIgnoreCase("NEWS"));
    }

    /**
     * Hard cutover to Gemini with a Claude fallback, per the user's explicit design - tries the
     * configured primary client first; any failure - the call itself throwing (network, schema
     * rejection, rate limit) OR a successfully-returned response that then fails to parse (a real
     * case found live: Gemini can return HTTP 200 with truncated/invalid JSON, which only
     * surfaces once {@link #parseResult} tries to read it) - falls back to Claude rather than
     * failing the document outright. Parsing is deliberately inside the try, not after it, so a
     * "succeeded but garbage" response is caught the same as an outright call failure. {@code
     * useGemini} is a plain Spring property (no admin UI, no DB flag) so switching back to
     * Claude-only is a config change, not a code change.
     */
    @Override
    public ExtractionResult extract(DocumentContext context) {
        if (!useGemini) {
            return parseResult(claudeClient.extractRawJson(context.documentText()));
        }
        try {
            return parseResult(geminiClient.extractRawJson(context.documentText()));
        } catch (Exception e) {
            log.warn("Gemini news extraction failed, falling back to Claude: {}", e.getMessage());
            return parseResult(claudeClient.extractRawJson(context.documentText()));
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
            // Module 2.7: which graph entity this impact relates to and how - e.g. companyName
            // "Kaynes" BENEFICIARY_OF relatedEntityName "Semiconductor PLI" (relatedEntityType
            // GOVERNMENT_SCHEME). All three are optional together - not every impact resolves to
            // a clean graph edge (plain sentiment with no identifiable scheme/theme/customer).
            addIfPresent(facts, "relatedEntityName", impact.relatedEntityName(), confidence, group);
            addIfPresent(facts, "relatedEntityType", impact.relatedEntityType(), confidence, group);
            addIfPresent(facts, "relationshipType", impact.relationshipType(), confidence, group);
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

            If the news names or implies a specific OTHER entity that explains WHY this company is
            affected - a government scheme, a broader industry theme, a customer, a competitor -
            also extract:
            - relatedEntityName: that other entity's name, e.g. "Semiconductor PLI", "EMS",
              "Ministry of Defence" - empty if there's no clean, specific entity to name (a vague
              "market conditions" is not a specific entity)
            - relatedEntityType: exactly one of CUSTOMER, THEME, GOVERNMENT_SCHEME, COMPETITOR -
              empty if relatedEntityName is empty
            - relationshipType: exactly one of CUSTOMER_OF, SUPPLIER_OF, COMPETES_WITH,
              SUBSIDIARY_OF, PART_OF_THEME, BENEFICIARY_OF, AFFECTED_BY, EXPORTS_TO,
              USES_COMMODITY, PARTNER_OF, EXECUTES_FOR, OPERATES_IN - whichever best describes how
              the company relates to relatedEntityName (e.g. a company benefiting from a
              government scheme is BENEFICIARY_OF that scheme; a company entering a new industry
              theme is PART_OF_THEME) - empty if relatedEntityName is empty

            Example: "The government announced a new Semiconductor PLI scheme. Kaynes Technology
            welcomed the scheme." -> companyName "Kaynes Technology", relatedEntityName
            "Semiconductor PLI", relatedEntityType GOVERNMENT_SCHEME, relationshipType
            BENEFICIARY_OF.

            If the news doesn't materially affect any identifiable company, return an empty
            impacts list. Do not invent companies that aren't named or clearly implied by the
            text, and do not include a company that's only mentioned in passing with no real
            impact.

            Document text:
            %s
            """.formatted(documentText);
    }

    /**
     * The one JSON schema both {@link ClaudeNewsExtractionClient} and {@link GeminiNewsExtractionClient}
     * ask for - defined once so the two providers can never quietly drift out of sync. {@link #buildOutputConfig}
     * wraps this same map into Anthropic's {@code OutputConfig}/{@code JsonOutputFormat} shape;
     * {@code GeminiNewsExtractionClient} passes it straight to Gemini's {@code responseSchema()},
     * which accepts a raw map with no dedicated schema-builder type required.
     */
    static Map<String, Object> buildSchemaMap() {
        Map<String, Object> impactSchema = Map.ofEntries(
            Map.entry("type", "object"),
            Map.entry("properties", Map.ofEntries(
                Map.entry("companyName", Map.of("type", "string")),
                Map.entry("direction", Map.of("type", "string", "enum", List.of("POSITIVE", "NEGATIVE", "NEUTRAL"))),
                Map.entry("signal", Map.of("type", "string")),
                Map.entry("impactSummary", Map.of("type", "string")),
                Map.entry("confidence", Map.of("type", "integer", "description", "0-100 confidence in this extraction.")),
                Map.entry("relatedEntityName", Map.of("type", "string", "description", "Empty string if there's no specific related entity.")),
                Map.entry("relatedEntityType", Map.of("type", "string", "enum", List.of(
                    "", "CUSTOMER", "THEME", "GOVERNMENT_SCHEME", "COMPETITOR"
                ))),
                Map.entry("relationshipType", Map.of("type", "string", "enum", List.of(
                    "", "CUSTOMER_OF", "SUPPLIER_OF", "COMPETES_WITH", "SUBSIDIARY_OF", "PART_OF_THEME",
                    "BENEFICIARY_OF", "AFFECTED_BY", "EXPORTS_TO", "USES_COMMODITY", "PARTNER_OF",
                    "EXECUTES_FOR", "OPERATES_IN"
                )))
            )),
            Map.entry("required", List.of(
                "companyName", "direction", "signal", "impactSummary", "confidence",
                "relatedEntityName", "relatedEntityType", "relationshipType"
            )),
            Map.entry("additionalProperties", false)
        );
        Map<String, Object> impactsArraySchema = Map.of("type", "array", "items", impactSchema);
        Map<String, Object> rootProperties = Map.of("impacts", impactsArraySchema);

        return Map.of(
            "type", "object",
            "properties", rootProperties,
            "required", List.of("impacts"),
            "additionalProperties", false
        );
    }

    static OutputConfig buildOutputConfig() {
        Map<String, Object> schemaMap = buildSchemaMap();

        JsonOutputFormat.Schema schema = JsonOutputFormat.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from(schemaMap.get("type")))
            .putAdditionalProperty("properties", JsonValue.from(schemaMap.get("properties")))
            .putAdditionalProperty("required", JsonValue.from(schemaMap.get("required")))
            .putAdditionalProperty("additionalProperties", JsonValue.from(schemaMap.get("additionalProperties")))
            .build();

        return OutputConfig.builder()
            .format(JsonOutputFormat.builder().schema(schema).build())
            .build();
    }
}
