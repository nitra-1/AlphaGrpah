package com.alphagraph.corporate.knowledge;

import com.alphagraph.corporate.api.Sentiment;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Stage 1: Document Understanding. Runs ONE lightweight Claude call per document, producing a
 * {@link DocumentClassification} - document type, topics, entities, summary, sentiment, and which
 * Stage 2 extractors are recommended. Deliberately does NOT extract structured business facts
 * (order values, guidance figures, ...) - that's Stage 2's job, run only by the specific
 * {@link DocumentExtractor}s this classification routes to (see {@link DocumentRouter}), each with
 * its own narrow prompt. Keeping this stage lean is what lets Stage 2 stay focused as more
 * extractors are added later (a future PatentExtractor, NewsExtractor, ...) without this prompt
 * growing to cover every domain at once.
 *
 * <p>This is the only class in the corporate module that calls Claude for classification; every
 * Stage 2 extractor makes its own separate, narrower call.
 */
@Component
public class DocumentIntelligenceEngine {

    private static final Logger log = LoggerFactory.getLogger(DocumentIntelligenceEngine.class);

    /** Bumped whenever the classification prompt/schema changes meaningfully - carried onto every {@link com.alphagraph.corporate.api.DocumentSummary}. */
    static final int PROMPT_VERSION = 3;

    // Sonnet 5, matching every other extractor's cost/quality call - classification is a bounded
    // structured-extraction task (fixed field set, enumerable sentiment), not open-ended reasoning.
    // Package-private, not private: ClaudeDocumentClassificationClient builds its own
    // MessageCreateParams from these, same model/token budget this class always used before the
    // Gemini pilot split provider-calling logic out into
    // ClaudeDocumentClassificationClient/GeminiDocumentClassificationClient.
    static final Model MODEL = Model.CLAUDE_SONNET_5;
    static final long MAX_TOKENS = 4096L;

    private final ClaudeDocumentClassificationClient claudeClient;
    private final GeminiDocumentClassificationClient geminiClient;
    private final ObjectMapper objectMapper;
    private final boolean useGeminiForNews;

    public DocumentIntelligenceEngine(
        ClaudeDocumentClassificationClient claudeClient, GeminiDocumentClassificationClient geminiClient, ObjectMapper objectMapper,
        @Value("${alphagraph.corporate.stage1-classification.use-gemini-for-news:true}") boolean useGeminiForNews
    ) {
        this.claudeClient = claudeClient;
        this.geminiClient = geminiClient;
        this.objectMapper = objectMapper;
        this.useGeminiForNews = useGeminiForNews;
    }

    /**
     * Returns the Stage 1 classification for one document's already-extracted text. Never
     * re-parses a PDF. {@code source} (see {@link com.alphagraph.corporate.api.DocumentSource})
     * gates the Gemini pilot: only {@code "NEWS"}-sourced documents are eligible, since Stage 1
     * decides which Stage 2 extractor runs at all for EVERY document type, including unreleased
     * financial filings - a misclassification there silently misroutes a document, not just
     * produces one bad fact, so this stays Claude-only for every other source unconditionally.
     * Same hard-cutover-with-fallback shape as {@link NewsExtractor#extract}: parsing happens
     * inside the try, not after it, so a "succeeded but garbage" Gemini response is caught the
     * same as an outright call failure.
     */
    public DocumentClassification classify(String documentText, String source) {
        if (!useGeminiForNews || !"NEWS".equals(source)) {
            return parseClassification(claudeClient.extractRawJson(documentText));
        }
        try {
            return parseClassification(geminiClient.extractRawJson(documentText));
        } catch (Exception e) {
            log.warn("Gemini document classification failed, falling back to Claude: {}", e.getMessage());
            return parseClassification(claudeClient.extractRawJson(documentText));
        }
    }

    DocumentClassification parseClassification(String rawJson) {
        LlmClassificationResponse response;
        try {
            response = objectMapper.readValue(rawJson, LlmClassificationResponse.class);
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

        return new DocumentClassification(
            response.documentType(), response.topics(), response.entities(), response.summary(),
            sentiment, response.confidence(), response.recommendedExtractors()
        );
    }

    /**
     * Lowercased, non-alphanumeric-stripped, so a downstream lookup by a known key ("orderValue")
     * survives minor LLM key-naming variance ("Order Value", "order_value"). Public - every Stage
     * 2 {@link DocumentExtractor} normalizes its own fact keys with this exact same function
     * before constructing an {@link ExtractedFact}, so a fact written by any extractor is always
     * found the same way by any downstream reader.
     */
    public static String normalizeFactType(String rawKey) {
        return rawKey.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    static String buildPrompt(String documentText) {
        return """
            You are performing a first-pass triage read of one corporate document (an exchange
            announcement, quarterly result, investor presentation, or similar filing from an
            Indian listed company). Your job is ONLY to understand what this document is and route
            it - do NOT extract specific figures, values, or guidance numbers; that is done by a
            separate, specialized pass later.

            Produce:
            - documentType: a short label for what kind of document this is (e.g.
              "ORDER_ANNOUNCEMENT", "CAPACITY_EXPANSION", "FINANCIAL_RESULT",
              "MANAGEMENT_COMMENTARY", "CORPORATE_ACTION", "GENERAL_UPDATE") - use your judgment,
              this is not a fixed list
            - topics: open-ended tags describing what the document is about (e.g. "Defence",
              "Radar"). In addition, whenever the document genuinely describes one of these named
              corporate event categories, include that EXACT category name as one of the topics
              (verbatim, so downstream rule engines can match on it reliably) - do not include one
              that doesn't apply:
                Large Order, Capacity Expansion, New Plant, Acquisition, Merger, Joint Venture,
                PLI Approval, Patent, Export Approval, Government Contract, Debt Raising,
                Promoter Buying, Promoter Selling
            - entities: the key companies/organizations named in the document (e.g. the filing
              company itself, customers, counterparties) - just names, not classification
            - summary: one or two sentences restating what the document says
            - sentiment: POSITIVE, NEGATIVE, or NEUTRAL - the document's overall tone
            - confidence: 0-100, your confidence in this classification
            - recommendedExtractors: which specialized processing this document needs, using short
              uppercase identifiers. Use "ORDER" if the document describes a new order, tender
              win, order execution update, order cancellation, or order completion. Use
              "MANAGEMENT" if the document contains forward-looking management commentary such as
              revenue/margin guidance, demand outlook, pricing, competition, capex plans, or risk
              commentary. Use "FINANCIAL_RESULTS" if the document reports the company's own
              quarterly or annual Financial Results - a table of Revenue/Sales, Profit, and EPS
              figures (typically a Board Meeting Outcome filed under SEBI Regulation 33) - not
              merely a mention of financial performance in prose, an actual results table. Use
              "NEWS" if this is a general news item, government notification, or
              policy announcement (not a company's own filing) that names or materially affects
              one or more companies - e.g. a sector policy change, a government scheme, an
              industry-wide development. If none applies, or another kind of specialized
              processing seems relevant, suggest a short descriptive uppercase identifier of your
              own, or return an empty list if nothing specialized applies.

            Document text:
            %s
            """.formatted(documentText);
    }

    /**
     * The one JSON schema both {@link ClaudeDocumentClassificationClient} and
     * {@link GeminiDocumentClassificationClient} ask for - defined once so the two providers can
     * never quietly drift out of sync. {@link #buildOutputConfig} wraps this same map into
     * Anthropic's {@code OutputConfig}/{@code JsonOutputFormat} shape; {@link GeminiSchemaTranslator}
     * translates it into Gemini's typed {@code Schema} shape.
     */
    static Map<String, Object> buildSchemaMap() {
        Map<String, Object> stringArraySchema = Map.of("type", "array", "items", Map.of("type", "string"));

        Map<String, Object> rootProperties = Map.of(
            "documentType", Map.of("type", "string", "description", "What kind of document this is."),
            "topics", stringArraySchema,
            "entities", Map.of("type", "array", "items", Map.of("type", "string"), "description", "Key companies/organizations named in the document."),
            "summary", Map.of("type", "string", "description", "One or two sentence summary."),
            "sentiment", Map.of("type", "string", "enum", List.of("POSITIVE", "NEGATIVE", "NEUTRAL")),
            "confidence", Map.of("type", "integer", "description", "0-100 confidence in this classification."),
            "recommendedExtractors", Map.of(
                "type", "array", "items", Map.of("type", "string"),
                "description", "Short uppercase identifiers for specialized processing this document needs, e.g. ORDER, MANAGEMENT."
            )
        );

        return Map.of(
            "type", "object",
            "properties", rootProperties,
            "required", List.of("documentType", "topics", "entities", "summary", "sentiment", "confidence", "recommendedExtractors"),
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
