package com.alphagraph.corporate.relationships;

import com.alphagraph.corporate.api.EntityType;
import com.alphagraph.corporate.api.RelationshipType;
import com.alphagraph.corporate.knowledge.ExtractedFact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The only writer of {@code knowledge.relationship} edges - per the user's explicit design,
 * Stage 2 extractors only ever emit canonical facts (which entity, which relationship type); this
 * class resolves those facts into entity IDs and creates the edges. Called synchronously, in
 * process, right after {@code corporate.knowledge.KnowledgeExtractionOrchestrator} writes a
 * document's facts - not a real message bus (Kafka remains "future" everywhere else in this
 * project), but the same logical separation: extractors never touch the graph directly.
 *
 * <p>Two shapes of input, handled differently:
 * <ul>
 *   <li>A fact group carrying {@code companyname} (from {@code NewsExtractor}) - that company is
 *   the edge's "from" side.</li>
 *   <li>A fact group with no {@code companyname} (from {@code ManagementExtractor}, describing the
 *   reporting company's own statement) - the document's own instrument is the "from" side.</li>
 * </ul>
 * Either way, a group only produces an edge if it also carries {@code relatedEntityName}/
 * {@code relatedEntityType}/{@code relationshipType} - most groups (a plain revenue guidance
 * percentage, a purely sentiment-only news impact) don't, and produce nothing here, the same
 * "zero is a valid outcome" precedent as every other engine in this module.
 *
 * <p>Order facts are a third, ungrouped case ({@code factGroup} is null, per
 * {@code corporate.knowledge.OrderExtractor}'s at-most-one-order design): a
 * {@code customer}/{@code orderlifecyclestage} pair always means the reporting company
 * EXECUTES_FOR that customer - a fixed semantic that doesn't need an LLM-supplied relationship
 * type the way News/Management's more varied relationships do.
 */
@Component
public class RelationshipBuilder {

    private static final Logger log = LoggerFactory.getLogger(RelationshipBuilder.class);

    private final EntityResolver entityResolver;
    private final RelationshipWriter relationshipWriter;
    private final CompetitorGroupExpander competitorGroupExpander;

    public RelationshipBuilder(
        EntityResolver entityResolver, RelationshipWriter relationshipWriter, CompetitorGroupExpander competitorGroupExpander
    ) {
        this.entityResolver = entityResolver;
        this.relationshipWriter = relationshipWriter;
        this.competitorGroupExpander = competitorGroupExpander;
    }

    /** Expands every curated {@code knowledge.competitor_group} into pairwise COMPETES_WITH edges. Idempotent. */
    public void expandCompetitorGroups() {
        competitorGroupExpander.expandAll();
    }

    public void build(UUID documentId, String symbol, List<ExtractedFact> facts) {
        Map<UUID, List<ExtractedFact>> byGroup = facts.stream()
            .filter(f -> f.factGroup() != null)
            .collect(Collectors.groupingBy(ExtractedFact::factGroup));

        for (List<ExtractedFact> group : byGroup.values()) {
            try {
                buildFromGroup(documentId, symbol, group);
            } catch (Exception e) {
                log.error("Failed to build relationship from a fact group in document {}: {}", documentId, e.getMessage(), e);
            }
        }

        try {
            buildFromOrderFacts(documentId, symbol, facts);
        } catch (Exception e) {
            log.error("Failed to build order relationship for document {}: {}", documentId, e.getMessage(), e);
        }
    }

    private void buildFromGroup(UUID documentId, String symbol, List<ExtractedFact> group) {
        Map<String, ExtractedFact> byType = group.stream()
            .collect(Collectors.toMap(ExtractedFact::factType, f -> f, (a, b) -> a));

        ExtractedFact relatedNameFact = byType.get("relatedentityname");
        ExtractedFact relatedTypeFact = byType.get("relatedentitytype");
        ExtractedFact relationshipTypeFact = byType.get("relationshiptype");
        if (relatedNameFact == null || relatedTypeFact == null || relationshipTypeFact == null) {
            return;
        }

        EntityType relatedType;
        RelationshipType relationshipType;
        try {
            relatedType = EntityType.valueOf(relatedTypeFact.value().trim().toUpperCase());
            relationshipType = RelationshipType.valueOf(relationshipTypeFact.value().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Document {} produced an unrecognized entity/relationship type ({}/{}), skipping",
                documentId, relatedTypeFact.value(), relationshipTypeFact.value());
            return;
        }

        ExtractedFact companyNameFact = byType.get("companyname");
        UUID fromEntityId;
        double confidence;
        if (companyNameFact != null) {
            fromEntityId = entityResolver.resolve(EntityType.COMPANY, companyNameFact.value());
            confidence = companyNameFact.extractionConfidence();
        } else if (symbol != null) {
            fromEntityId = entityResolver.resolve(EntityType.COMPANY, symbol);
            confidence = relatedNameFact.extractionConfidence();
        } else {
            // No companyname fact in the group (a Management-shaped statement) and no document-
            // level symbol either (a NEWS-sourced document with no fixed instrument) - there's no
            // way to know who the "from" side is.
            return;
        }

        UUID toEntityId = entityResolver.resolve(relatedType, relatedNameFact.value());
        relationshipWriter.write(fromEntityId, relationshipType, toEntityId, documentId, confidence, "RELATIONSHIP_BUILDER");
    }

    private void buildFromOrderFacts(UUID documentId, String symbol, List<ExtractedFact> facts) {
        if (symbol == null) {
            return;
        }

        Map<String, ExtractedFact> byType = facts.stream()
            .filter(f -> f.factGroup() == null)
            .collect(Collectors.toMap(ExtractedFact::factType, f -> f, (a, b) -> a));

        ExtractedFact lifecycleFact = byType.get("orderlifecyclestage");
        ExtractedFact customerFact = byType.get("customer");
        if (lifecycleFact == null || customerFact == null || customerFact.value().isBlank()) {
            return;
        }

        UUID companyEntityId = entityResolver.resolve(EntityType.COMPANY, symbol);
        UUID customerEntityId = entityResolver.resolve(EntityType.CUSTOMER, customerFact.value());
        relationshipWriter.write(
            companyEntityId, RelationshipType.EXECUTES_FOR, customerEntityId,
            documentId, customerFact.extractionConfidence(), "RELATIONSHIP_BUILDER"
        );
    }
}
