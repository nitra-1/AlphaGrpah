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

        // Event-level group - one per document, present whenever the root economicRelevance
        // classification came back at all (it's a required schema field). This single group is
        // what corporate.news.EconomicEventParser looks for to build the Economic Event row;
        // company/sector groups below are found by their own distinguishing key instead.
        if (!isBlank(response.economicRelevance())) {
            UUID eventGroup = UUID.randomUUID();
            double eventConfidence = response.eventConfidence();
            facts.add(new ExtractedFact(
                DocumentIntelligenceEngine.normalizeFactType("economicRelevance"), response.economicRelevance().trim(), "", eventConfidence, null, eventGroup
            ));
            addIfPresent(facts, "theme", response.theme(), eventConfidence, eventGroup);
            addIfPresent(facts, "eventDirection", response.eventDirection(), eventConfidence, eventGroup);
            addIfPresent(facts, "magnitude", response.magnitude(), eventConfidence, eventGroup);
            addIfPresent(facts, "horizon", response.horizon(), eventConfidence, eventGroup);
        }

        List<LlmSectorImpact> sectorImpacts = response.sectorImpacts() == null ? List.of() : response.sectorImpacts();
        for (LlmSectorImpact sectorImpact : sectorImpacts) {
            if (isBlank(sectorImpact.sector()) || isBlank(sectorImpact.direction()) || isBlank(sectorImpact.strength())) {
                continue;
            }
            UUID group = UUID.randomUUID();
            double confidence = sectorImpact.confidence();
            facts.add(new ExtractedFact(
                DocumentIntelligenceEngine.normalizeFactType("sectorName"), sectorImpact.sector().trim(), "", confidence, null, group
            ));
            facts.add(new ExtractedFact(
                DocumentIntelligenceEngine.normalizeFactType("sectorDirection"), sectorImpact.direction().trim(), "", confidence, null, group
            ));
            facts.add(new ExtractedFact(
                DocumentIntelligenceEngine.normalizeFactType("sectorStrength"), sectorImpact.strength().trim(), "", confidence, null, group
            ));
            addIfPresent(facts, "mechanism", sectorImpact.mechanism(), confidence, group);
        }

        List<LlmNewsCompanyImpact> impacts = response.impacts() == null ? List.of() : response.impacts();
        for (LlmNewsCompanyImpact impact : impacts) {
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
            // News & Economic Discovery rework: which sector this company's exposure relates to,
            // the exposure mechanism (DIRECT/SUPPLY_CHAIN/INPUT_COST/DEMAND/REGULATORY/
            // COMPETITIVE/MACRO), and impact strength - corporate.news.EconomicEventParser reads
            // these alongside the existing fields to build a CompanyExposure row.
            addIfPresent(facts, "sector", impact.sector(), confidence, group);
            addIfPresent(facts, "exposureType", impact.exposureType(), confidence, group);
            addIfPresent(facts, "impactStrength", impact.impactStrength(), confidence, group);
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
            You are a specialized extractor for Indian financial/economic news. Read the article
            and produce THREE things: an overall economic classification of the article itself,
            which SECTORS it affects (if any), and which COMPANIES it affects (if any, tracked or
            not - name them freely, in your own words, exactly as the text does or clearly
            implies; do not worry about whether AlphaGraph tracks them).

            STEP 1 - Classify the article itself:
            - economicRelevance: exactly one of ECONOMIC, MARKET, SECTOR, COMPANY, COMMODITY,
              REGULATORY, GEOPOLITICAL, MACRO, NON_ECONOMIC, LOW_CONFIDENCE. Use NON_ECONOMIC for
              sports/entertainment/celebrity/crime/pure-politics content with no economic angle.
              Use LOW_CONFIDENCE only when you genuinely cannot tell.
            - theme: a short label for the underlying economic event/theme, e.g.
              "DEFENCE_SPENDING", "CRUDE_OIL_PRICE_RISE", "RBI_RATE_DECISION" - empty string if
              economicRelevance is NON_ECONOMIC.
            - eventDirection: POSITIVE, NEGATIVE, MIXED, or NEUTRAL for the economy/market overall
              (not any one company).
            - magnitude: HIGH, MEDIUM, or LOW - how significant this event is.
            - eventConfidence: 0-100, your confidence in this classification.
            - horizon: SHORT_TERM (days), MEDIUM_TERM (months), or LONG_TERM (years) - how long
              this event's effects plausibly matter.
            If economicRelevance is NON_ECONOMIC or LOW_CONFIDENCE, leave sectorImpacts and
            impacts as empty lists - do not force a sector/company mapping onto irrelevant news.

            STEP 2 - For EVERY sector the news plausibly affects (there can be several, with
            DIFFERENT directions - e.g. crude oil rising is NEGATIVE for Airlines/Paints/Tyres but
            POSITIVE for Oil Producers/Oil Services - never collapse this into one verdict for
            "Oil"), extract:
            - sector: the sector name, e.g. "Defence", "Airlines", "Oil & Gas"
            - direction: POSITIVE, NEGATIVE, MIXED, or NEUTRAL for this specific sector
            - strength: HIGH, MEDIUM, or LOW
            - confidence: 0-100
            - mechanism: one short phrase for WHY this sector is affected, e.g. "higher fuel
              cost", "government procurement", "input cost relief" - empty if not clear.

            STEP 3 - For EVERY company the news materially affects, extract:
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
            - sector: which sector this company's exposure belongs to (should normally match one
              of the sectorImpacts entries above)
            - exposureType: exactly one of DIRECT (company directly produces/sells the affected
              product/service), SUPPLY_CHAIN (supplies the affected industry), INPUT_COST
              (affected by cost of a commodity/input), DEMAND (benefits from increased demand),
              REGULATORY (directly affected by a government/regulatory change), COMPETITIVE (one
              company's gain is a rival's disadvantage), MACRO (interest rates/currency/inflation/
              liquidity, not sector-specific)
            - impactStrength: HIGH, MEDIUM, or LOW

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
            welcomed the scheme." -> companyName "Kaynes Technology", sector "Electronics",
            exposureType REGULATORY, relatedEntityName "Semiconductor PLI", relatedEntityType
            GOVERNMENT_SCHEME, relationshipType BENEFICIARY_OF.

            Do not invent companies or sectors that aren't named or clearly implied by the text,
            and do not include a company that's only mentioned in passing with no real impact.

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
                Map.entry("sector", Map.of("type", "string", "description", "Which sector this company's exposure belongs to.")),
                Map.entry("exposureType", Map.of("type", "string", "enum", List.of(
                    "DIRECT", "SUPPLY_CHAIN", "INPUT_COST", "DEMAND", "REGULATORY", "COMPETITIVE", "MACRO"
                ))),
                Map.entry("impactStrength", Map.of("type", "string", "enum", List.of("HIGH", "MEDIUM", "LOW"))),
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
                "sector", "exposureType", "impactStrength",
                "relatedEntityName", "relatedEntityType", "relationshipType"
            )),
            Map.entry("additionalProperties", false)
        );
        Map<String, Object> sectorImpactSchema = Map.ofEntries(
            Map.entry("type", "object"),
            Map.entry("properties", Map.ofEntries(
                Map.entry("sector", Map.of("type", "string")),
                Map.entry("direction", Map.of("type", "string", "enum", List.of("POSITIVE", "NEGATIVE", "MIXED", "NEUTRAL"))),
                Map.entry("strength", Map.of("type", "string", "enum", List.of("HIGH", "MEDIUM", "LOW"))),
                Map.entry("confidence", Map.of("type", "integer", "description", "0-100 confidence in this extraction.")),
                Map.entry("mechanism", Map.of("type", "string", "description", "Empty string if there's no clear, specific mechanism to name."))
            )),
            Map.entry("required", List.of("sector", "direction", "strength", "confidence", "mechanism")),
            Map.entry("additionalProperties", false)
        );

        Map<String, Object> impactsArraySchema = Map.of("type", "array", "items", impactSchema);
        Map<String, Object> sectorImpactsArraySchema = Map.of("type", "array", "items", sectorImpactSchema);
        Map<String, Object> rootProperties = Map.ofEntries(
            Map.entry("economicRelevance", Map.of("type", "string", "enum", List.of(
                "ECONOMIC", "MARKET", "SECTOR", "COMPANY", "COMMODITY", "REGULATORY", "GEOPOLITICAL",
                "MACRO", "NON_ECONOMIC", "LOW_CONFIDENCE"
            ))),
            Map.entry("theme", Map.of("type", "string", "description", "Empty string if economicRelevance is NON_ECONOMIC.")),
            Map.entry("eventDirection", Map.of("type", "string", "enum", List.of("POSITIVE", "NEGATIVE", "MIXED", "NEUTRAL"))),
            Map.entry("magnitude", Map.of("type", "string", "enum", List.of("HIGH", "MEDIUM", "LOW"))),
            Map.entry("eventConfidence", Map.of("type", "integer", "description", "0-100 confidence in this classification.")),
            Map.entry("horizon", Map.of("type", "string", "enum", List.of("SHORT_TERM", "MEDIUM_TERM", "LONG_TERM"))),
            Map.entry("sectorImpacts", sectorImpactsArraySchema),
            Map.entry("impacts", impactsArraySchema)
        );

        return Map.of(
            "type", "object",
            "properties", rootProperties,
            "required", List.of(
                "economicRelevance", "theme", "eventDirection", "magnitude", "eventConfidence", "horizon",
                "sectorImpacts", "impacts"
            ),
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
