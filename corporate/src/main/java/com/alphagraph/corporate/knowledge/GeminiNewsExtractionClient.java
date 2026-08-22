package com.alphagraph.corporate.knowledge;

import com.google.genai.Client;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Schema;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Gemini free-tier pilot's own call for {@link NewsExtractor} - same prompt text
 * ({@link NewsExtractor#buildPrompt}), same logical JSON schema ({@link NewsExtractor#buildSchemaMap})
 * as {@link ClaudeNewsExtractionClient}, so {@link NewsExtractor#parseResult} works identically
 * regardless of which client actually produced the raw JSON.
 *
 * <p>Unlike Anthropic's {@code OutputConfig} (which accepts the schema as a lightweight
 * {@code JsonValue}-wrapped map), Gemini's SDK requires a genuinely typed {@link Schema} object -
 * confirmed via the compiler, not the SDK's own docs, which had described a raw-map path that
 * doesn't actually exist in the installed 1.67.0 jar. {@link #toGeminiSchema} translates
 * {@link NewsExtractor#buildSchemaMap}'s map into that typed shape, so the schema is still defined
 * exactly once - only the wire-level building differs per provider. One real, disclosed difference:
 * Gemini's {@code Schema} type has no {@code additionalProperties} concept at all (no builder
 * method exists for it), so Gemini's structured output is somewhat less strict than Claude's on
 * this one dimension - not fixable, just a known gap between the two providers' schema dialects.
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
            .responseSchema(toGeminiSchema(NewsExtractor.buildSchemaMap()))
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
    private static final List<String> OPTIONAL_WHEN_BLANK_FIELDS = List.of(
        "relatedEntityName", "relatedEntityType", "relationshipType"
    );

    @SuppressWarnings("unchecked")
    private static Schema toGeminiSchema(Map<String, Object> map) {
        Schema.Builder builder = Schema.builder().type((String) map.get("type"));

        if (map.get("description") instanceof String description) {
            builder.description(description);
        }
        if (map.get("enum") instanceof List<?> enumValues) {
            List<String> withoutBlankMember = ((List<String>) enumValues).stream().filter(v -> !v.isBlank()).toList();
            builder.enum_(withoutBlankMember.isEmpty() ? (List<String>) enumValues : withoutBlankMember);
        }
        if (map.get("properties") instanceof Map<?, ?> rawProperties) {
            Map<String, Schema> properties = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawProperties.entrySet()) {
                properties.put((String) entry.getKey(), toGeminiSchema((Map<String, Object>) entry.getValue()));
            }
            builder.properties(properties);
        }
        if (map.get("required") instanceof List<?> required) {
            List<String> withoutOptionalFields = ((List<String>) required).stream()
                .filter(name -> !OPTIONAL_WHEN_BLANK_FIELDS.contains(name))
                .toList();
            builder.required(withoutOptionalFields);
        }
        if (map.get("items") instanceof Map<?, ?> items) {
            builder.items(toGeminiSchema((Map<String, Object>) items));
        }
        return builder.build();
    }
}
