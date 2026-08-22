package com.alphagraph.corporate.knowledge;

import com.google.genai.types.Schema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirms the {@code optionalWhenBlankFields} parameterization behaves correctly for both real
 * callers: {@link NewsExtractor}'s schema (a non-empty set - {@code relatedEntityName}/
 * {@code relatedEntityType}/{@code relationshipType} deliberately allow an empty-string enum
 * member) and {@link DocumentIntelligenceEngine}'s schema (an empty set - no field there uses that
 * convention).
 */
class GeminiSchemaTranslatorTest {

    @Test
    void emptySetLeavesRequiredAndEnumsUntouched() {
        Schema schema = GeminiSchemaTranslator.toGeminiSchema(DocumentIntelligenceEngine.buildSchemaMap(), Set.of());

        assertThat(schema.required()).contains(List.of(
            "documentType", "topics", "entities", "summary", "sentiment", "confidence", "recommendedExtractors"
        ));
        Schema sentimentSchema = schema.properties().orElseThrow().get("sentiment");
        assertThat(sentimentSchema.enum_()).contains(List.of("POSITIVE", "NEGATIVE", "NEUTRAL"));
    }

    @Test
    void nonEmptySetDropsBlankEnumMembersAndRemovesFieldsFromRequired() {
        Schema schema = GeminiSchemaTranslator.toGeminiSchema(
            NewsExtractor.buildSchemaMap(), Set.of("relatedEntityName", "relatedEntityType", "relationshipType")
        );

        Schema impactSchema = schema.properties().orElseThrow().get("impacts").items().orElseThrow();
        assertThat(impactSchema.required().orElseThrow())
            .contains("companyName", "direction", "signal", "impactSummary", "confidence")
            .doesNotContain("relatedEntityName", "relatedEntityType", "relationshipType");

        Schema relatedEntityTypeSchema = impactSchema.properties().orElseThrow().get("relatedEntityType");
        assertThat(relatedEntityTypeSchema.enum_().orElseThrow())
            .doesNotContain("")
            .contains("CUSTOMER", "THEME", "GOVERNMENT_SCHEME", "COMPETITOR");
    }

    @Test
    void nestedObjectAndArraySchemasTranslateRecursively() {
        Map<String, Object> nested = Map.of(
            "type", "object",
            "properties", Map.of("items", Map.of("type", "array", "items", Map.of("type", "string"))),
            "required", List.of("items")
        );

        Schema schema = GeminiSchemaTranslator.toGeminiSchema(nested, Set.of());

        Schema itemsArraySchema = schema.properties().orElseThrow().get("items");
        assertThat(itemsArraySchema.items()).isPresent();
    }
}
