package com.alphagraph.corporate.relationships;

import com.alphagraph.corporate.api.EntityType;
import com.alphagraph.corporate.api.RelationshipType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Expands {@code knowledge.competitor_group} membership into pairwise COMPETES_WITH edges - per
 * the user's explicit design, competitors are never inferred dynamically from free text; a group
 * is curated once here and expanded deterministically. Ships with one real, hand-curated group
 * (EMS - Electronics Manufacturing Services, the sector the News Engine's own worked example
 * names: Kaynes, Syrma SGS, Dixon Technologies, Avalon Technologies, PG Electroplast - none of
 * them in AlphaGraph's 8-name tracked instrument universe, which is exactly the point: the graph
 * models entities the tracked universe doesn't cover). Idempotent - safe to call on every
 * {@code KnowledgeExtractionOrchestrator} run, not just once at startup.
 */
@Component
class CompetitorGroupExpander {

    private static final String EMS_GROUP_NAME = "EMS";
    private static final List<String> EMS_MEMBERS = List.of(
        "Kaynes Technology", "Syrma SGS", "Dixon Technologies", "Avalon Technologies", "PG Electroplast"
    );

    private final EntityResolver entityResolver;
    private final RelationshipWriter relationshipWriter;
    private final JdbcTemplate jdbcTemplate;

    CompetitorGroupExpander(EntityResolver entityResolver, RelationshipWriter relationshipWriter, JdbcTemplate jdbcTemplate) {
        this.entityResolver = entityResolver;
        this.relationshipWriter = relationshipWriter;
        this.jdbcTemplate = jdbcTemplate;
    }

    void expandAll() {
        UUID groupId = ensureGroup(EMS_GROUP_NAME);
        List<UUID> memberIds = EMS_MEMBERS.stream()
            .map(name -> entityResolver.resolve(EntityType.COMPETITOR, name))
            .toList();
        memberIds.forEach(memberId -> ensureMembership(groupId, memberId));
        expandPairwiseCompetesWith(memberIds);
    }

    private UUID ensureGroup(String name) {
        List<UUID> existing = jdbcTemplate.query(
            "SELECT id FROM knowledge.competitor_group WHERE name = ?", (rs, rowNum) -> (UUID) rs.getObject("id"), name
        );
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        return jdbcTemplate.queryForObject(
            "INSERT INTO knowledge.competitor_group (name) VALUES (?) RETURNING id", UUID.class, name
        );
    }

    private void ensureMembership(UUID groupId, UUID entityId) {
        jdbcTemplate.update(
            "INSERT INTO knowledge.competitor_group_member (competitor_group_id, entity_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
            groupId, entityId
        );
    }

    private void expandPairwiseCompetesWith(List<UUID> memberIds) {
        for (int i = 0; i < memberIds.size(); i++) {
            for (int j = 0; j < memberIds.size(); j++) {
                if (i == j) {
                    continue;
                }
                relationshipWriter.write(
                    memberIds.get(i), RelationshipType.COMPETES_WITH, memberIds.get(j), null, 100.0, "COMPETITOR_GROUP_EXPANDER"
                );
            }
        }
    }
}
