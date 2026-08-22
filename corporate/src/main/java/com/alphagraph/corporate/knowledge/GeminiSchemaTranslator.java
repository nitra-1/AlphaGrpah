package com.alphagraph.corporate.knowledge;

import com.google.genai.types.Schema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Translates the plain-map JSON schemas this module's extractors already build for Anthropic
 * (e.g. {@link NewsExtractor#buildSchemaMap}, {@link DocumentIntelligenceEngine#buildSchemaMap})
 * into Gemini's own typed {@link Schema} shape - built once for {@code GeminiNewsExtractionClient}
 * and generalized here once a second caller ({@code GeminiDocumentClassificationClient}) needed
 * the identical translation. Two real, disclosed differences from Anthropic's schema dialect,
 * both confirmed against the real installed SDK (`com.google.genai:google-genai:1.67.0`), not
 * assumed from documentation:
 * <ul>
 *   <li>Gemini's {@code Schema} type has no {@code additionalProperties} concept at all (no
 *   builder method exists for it) - Gemini's structured output is unavoidably less strict than
 *   Claude's on this one dimension.</li>
 *   <li>Gemini rejects an empty-string enum member outright (a real {@code 400 INVALID_ARGUMENT
 *   ... enum[0]: cannot be empty}, not a guess) - some Anthropic schemas use an empty string to
 *   mean "not applicable" for an otherwise-required field. {@code optionalWhenBlankFields} names
 *   which fields use that convention for a given schema, so the empty enum member gets dropped and
 *   the field is removed from Gemini's {@code required} list instead, letting Gemini genuinely
 *   omit it - the caller's own {@code isBlank()}-style parsing already treats a missing value the
 *   same as an empty one, so no parsing change is needed on either side.</li>
 * </ul>
 */
final class GeminiSchemaTranslator {

    private GeminiSchemaTranslator() {
    }

    static Schema toGeminiSchema(Map<String, Object> map, Set<String> optionalWhenBlankFields) {
        return convert(map, optionalWhenBlankFields);
    }

    @SuppressWarnings("unchecked")
    private static Schema convert(Map<String, Object> map, Set<String> optionalWhenBlankFields) {
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
                properties.put((String) entry.getKey(), convert((Map<String, Object>) entry.getValue(), optionalWhenBlankFields));
            }
            builder.properties(properties);
        }
        if (map.get("required") instanceof List<?> required) {
            List<String> withoutOptionalFields = ((List<String>) required).stream()
                .filter(name -> !optionalWhenBlankFields.contains(name))
                .toList();
            builder.required(withoutOptionalFields);
        }
        if (map.get("items") instanceof Map<?, ?> items) {
            builder.items(convert((Map<String, Object>) items, optionalWhenBlankFields));
        }
        return builder.build();
    }
}
