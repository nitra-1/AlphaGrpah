package com.alphagraph.corporate.relationships;

import com.alphagraph.corporate.api.RelationshipEdge;
import com.alphagraph.corporate.api.RelationshipType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Reads {@code knowledge.relationship} edges back with both endpoints' names already resolved -
 * added in Module 2.9 (AI Analyst), the first consumer to actually traverse the graph Module 2.7
 * built. Until now, {@code corporate.relationships} only ever wrote edges (via
 * {@link RelationshipBuilder}/{@link RelationshipWriter}) or resolved a name to an id (via
 * {@link EntityResolver}) - nothing read an entity's connections back.
 */
@Component
public class RelationshipReader {

    private final JdbcTemplate jdbcTemplate;

    public RelationshipReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Every edge FROM this entity - e.g. "what does this company benefit from / compete with / supply". */
    public List<RelationshipEdge> findOutgoing(UUID fromEntityId) {
        return jdbcTemplate.query(
            """
            SELECT r.from_entity_id, fromE.canonical_name AS from_name, r.relationship_type,
                   r.to_entity_id, toE.canonical_name AS to_name, r.confidence
            FROM knowledge.relationship r
            JOIN knowledge.entity_master fromE ON fromE.id = r.from_entity_id
            JOIN knowledge.entity_master toE ON toE.id = r.to_entity_id
            WHERE r.from_entity_id = ?
            """,
            (rs, rowNum) -> new RelationshipEdge(
                (UUID) rs.getObject("from_entity_id"), rs.getString("from_name"),
                RelationshipType.valueOf(rs.getString("relationship_type")),
                (UUID) rs.getObject("to_entity_id"), rs.getString("to_name"), rs.getDouble("confidence")
            ),
            fromEntityId
        );
    }

    /** Every edge TO this entity - e.g. "which companies benefit from this scheme / are customers of this entity". */
    public List<RelationshipEdge> findIncoming(UUID toEntityId) {
        return jdbcTemplate.query(
            """
            SELECT r.from_entity_id, fromE.canonical_name AS from_name, r.relationship_type,
                   r.to_entity_id, toE.canonical_name AS to_name, r.confidence
            FROM knowledge.relationship r
            JOIN knowledge.entity_master fromE ON fromE.id = r.from_entity_id
            JOIN knowledge.entity_master toE ON toE.id = r.to_entity_id
            WHERE r.to_entity_id = ?
            """,
            (rs, rowNum) -> new RelationshipEdge(
                (UUID) rs.getObject("from_entity_id"), rs.getString("from_name"),
                RelationshipType.valueOf(rs.getString("relationship_type")),
                (UUID) rs.getObject("to_entity_id"), rs.getString("to_name"), rs.getDouble("confidence")
            ),
            toEntityId
        );
    }
}
