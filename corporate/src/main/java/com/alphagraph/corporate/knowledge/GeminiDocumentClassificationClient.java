package com.alphagraph.corporate.knowledge;

import com.google.genai.Client;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * The Gemini pilot's own call for Stage 1 classification, scoped to {@code NEWS}-sourced
 * documents only (see {@link DocumentIntelligenceEngine#classify}) - same prompt text
 * ({@link DocumentIntelligenceEngine#buildPrompt}), same logical JSON schema
 * ({@link DocumentIntelligenceEngine#buildSchemaMap}) as {@link ClaudeDocumentClassificationClient},
 * so {@link DocumentIntelligenceEngine#parseClassification} works identically regardless of which
 * client actually produced the raw JSON.
 *
 * <p>Reuses {@link GeminiSchemaTranslator}, the same map-to-typed-{@code Schema} translator built
 * for {@link GeminiNewsExtractionClient}. Stage 1's schema has no field like
 * {@code NewsExtractor}'s {@code relatedEntityType} that deliberately allows an empty-string enum
 * member to mean "not applicable" - {@code sentiment}'s enum has no blank option, and
 * {@code documentType} is free-form text, not an enum at all - so this client passes an empty
 * optional-when-blank field set (checked against the actual schema, not assumed).
 *
 * <p>Any failure here is wrapped as {@link IllegalStateException} without distinguishing error type
 * (Gemini's error hierarchy isn't as fully documented as Anthropic's) - this fallback path's whole
 * job is "any failure, for any reason, hands off to Claude", so {@link DocumentIntelligenceEngine}
 * only needs one exception type to catch regardless of provider.
 */
@Component
class GeminiDocumentClassificationClient implements DocumentClassificationExtractionClient {

    // Matches GeminiNewsExtractionClient's own findings: gemini-2.5-flash is deprecated for new
    // users (confirmed live via a real 404 naming this replacement), and an explicit output-token
    // cap avoids the truncated-JSON failure mode already found once for the NewsExtractor pilot.
    private static final String MODEL = "gemini-3.6-flash";
    private static final int MAX_OUTPUT_TOKENS = 2048;

    private static final Set<String> OPTIONAL_WHEN_BLANK_FIELDS = Set.of();

    private final Client client;

    GeminiDocumentClassificationClient(Client client) {
        this.client = client;
    }

    @Override
    public String extractRawJson(String documentText) {
        GenerateContentConfig config = GenerateContentConfig.builder()
            .responseMimeType("application/json")
            .responseSchema(GeminiSchemaTranslator.toGeminiSchema(DocumentIntelligenceEngine.buildSchemaMap(), OPTIONAL_WHEN_BLANK_FIELDS))
            .maxOutputTokens(MAX_OUTPUT_TOKENS)
            .build();

        try {
            GenerateContentResponse response = client.models.generateContent(MODEL, DocumentIntelligenceEngine.buildPrompt(documentText), config);
            String text = response.text();
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("Gemini returned an empty response for document classification");
            }
            return text;
        } catch (GenAiIOException e) {
            throw new IllegalStateException("Gemini API call failed during document classification: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Gemini API call failed during document classification: " + e.getMessage(), e);
        }
    }
}
