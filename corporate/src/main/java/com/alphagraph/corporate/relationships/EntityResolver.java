package com.alphagraph.corporate.relationships;

import com.alphagraph.corporate.api.EntityType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Array;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Resolves free text into an {@code entity_id}, creating a new {@code knowledge.entity_master}
 * row when nothing matches. Role-agnostic by design: resolution always searches across every
 * {@code EntityType} for a name match first (a name that first appeared as a COMPETITOR mention
 * and later as a genuine order CUSTOMER must resolve to the same row), and only uses the
 * caller-supplied {@code entityType} when actually creating a brand-new entity. Every non-seed
 * entity in the graph exists because some caller resolved it through here - nothing else writes
 * to {@code entity_master}.
 */
@Component
public class EntityResolver {

    private final JdbcTemplate jdbcTemplate;

    public EntityResolver(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID resolve(EntityType entityType, String rawName) {
        String trimmed = rawName.trim();
        String normalizedInput = EntityNameNormalizer.normalize(trimmed);

        List<CandidateRow> rows = jdbcTemplate.query(
            "SELECT id, canonical_name, aliases FROM knowledge.entity_master",
            (rs, rowNum) -> new CandidateRow(
                (UUID) rs.getObject("id"), rs.getString("canonical_name"), toStringArray(rs.getArray("aliases"))
            )
        );

        for (CandidateRow row : rows) {
            if (EntityNameNormalizer.matches(normalizedInput, EntityNameNormalizer.normalize(row.canonicalName()))) {
                return row.id();
            }
            for (String alias : row.aliases()) {
                if (EntityNameNormalizer.matches(normalizedInput, EntityNameNormalizer.normalize(alias))) {
                    return row.id();
                }
            }
        }

        return jdbcTemplate.queryForObject(
            "INSERT INTO knowledge.entity_master (entity_type, canonical_name) VALUES (?, ?) RETURNING id",
            UUID.class, entityType.name(), trimmed
        );
    }

    private static String[] toStringArray(Array sqlArray) {
        if (sqlArray == null) {
            return new String[0];
        }
        try {
            return (String[]) sqlArray.getArray();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read aliases array from knowledge.entity_master", e);
        }
    }

    private record CandidateRow(UUID id, String canonicalName, String[] aliases) {
    }
}
