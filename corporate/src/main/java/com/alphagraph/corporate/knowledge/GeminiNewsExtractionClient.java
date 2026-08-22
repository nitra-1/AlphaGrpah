package com.alphagraph.corporate.knowledge;

import com.google.genai.Client;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * The Gemini free-tier pilot's own call for {@link NewsExtractor} - same prompt text
 * ({@link NewsExtractor#buildPrompt}), same logical JSON schema ({@link NewsExtractor#buildSchemaMap})
 * as {@link ClaudeNewsExtractionClient}, so {@link NewsExtractor#parseResult} works identically
 * regardless of which client actually produced the raw JSON.
 *
 * <p>Unlike Anthropic's {@code OutputConfig} (which accepts the schema as a lightweight
 * {@code JsonValue}-wrapped map), Gemini's SDK requires a genuinely typed {@code Schema} object -
 * confirmed via the compiler, not the SDK's own docs, which had described a raw-map path that
 * doesn't actually exist in the installed 1.67.0 jar. {@link GeminiSchemaTranslator} translates
 * {@link NewsExtractor#buildSchemaMap}'s map into that typed shape (shared with
 * {@code GeminiDocumentClassificationClient} once Stage 1's own Gemini pilot needed the identical
 * translation), so the schema is still defined exactly once - only the wire-level building differs
 * per provider.
 *
 * <p>Any failure here is wrapped as {@link IllegalStateException} without distinguishing error type
 * (Gemini's error hierarchy isn't as fully documented as Anthropic's) - this fallback path's whole
 * job is "any failure, for any reason, hands off to Claude", so {@link NewsExtractor} only needs
 * one exception type to catch regardless of provider.
 */
@Component
class GeminiNewsExtractionClient implements NewsImpactExtractionClient {

    // gemini-2.5-flash is deprecated for new users as of this pilot (confirmed live via a real
    // 404: "This model models/gemini-2.5-flash is no longer available to new users. Please
    // update your code to use models/gemini-3.6-flash") - not the model this session's earlier
    // research described, which was already stale by the time this was implemented.
    private static final String MODEL = "gemini-3.6-flash";
    // Matches NewsExtractor.MAX_TOKENS's own budget - a real live run without an explicit cap
    // produced a truncated, invalid-JSON response (confirmed via a real JsonEOFException before
    // this was added), not just a theoretical risk.
    private static final int MAX_OUTPUT_TOKENS = 2048;

    private final Client client;

    GeminiNewsExtractionClient(Client client) {
        this.client = client;
    }

    @Override
    public String extractRawJson(String documentText) {
        GenerateContentConfig config = GenerateContentConfig.builder()
            .responseMimeType("application/json")
            .responseSchema(GeminiSchemaTranslator.toGeminiSchema(NewsExtractor.buildSchemaMap(), OPTIONAL_WHEN_BLANK_FIELDS))
            .maxOutputTokens(MAX_OUTPUT_TOKENS)
            .build();

        try {
            GenerateContentResponse response = client.models.generateContent(MODEL, NewsExtractor.buildPrompt(documentText), config);
            String text = response.text();
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("Gemini returned an empty response for news extraction");
            }
            return text;
        } catch (GenAiIOException e) {
            throw new IllegalStateException("Gemini API call failed during news extraction: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Gemini API call failed during news extraction: " + e.getMessage(), e);
        }
    }

    /**
     * Fields whose Anthropic schema deliberately allows an empty string to mean "not applicable"
     * (see {@code NewsExtractor.buildPrompt}: "empty if there's no clean, specific entity to
     * name"). Confirmed live against the real Gemini API, not assumed: Gemini's {@code Schema}
     * rejects an empty-string enum member outright ({@code 400 INVALID_ARGUMENT ...
     * enum[0]: cannot be empty}). Since {@code parseResult}'s own {@code isBlank()} checks already
     * treat a missing/null value the same as an empty one, the fix is to drop the empty member
     * from Gemini's enum AND drop these three fields from Gemini's {@code required} list, so
     * Gemini can genuinely omit them instead of being forced to always name a related entity.
     */
    private static final Set<String> OPTIONAL_WHEN_BLANK_FIELDS = Set.of(
        "relatedEntityName", "relatedEntityType", "relationshipType"
    );
}
