package com.alphagraph.corporate.relationships;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reverse lookup for display purposes only - {@code entity_id} to its {@code canonical_name}.
 * A separate, single-purpose class from {@link EntityResolver} (which only ever goes the other
 * direction: text to id) since nothing downstream should store a denormalized copy of an entity's
 * name once it has an {@code entity_id} to reference instead.
 */
@Component
public class EntityReader {

    private final JdbcTemplate jdbcTemplate;

    public EntityReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<String> findCanonicalName(UUID entityId) {
        List<String> names = jdbcTemplate.query(
            "SELECT canonical_name FROM knowledge.entity_master WHERE id = ?",
            (rs, rowNum) -> rs.getString("canonical_name"),
            entityId
        );
        return names.isEmpty() ? Optional.empty() : Optional.of(names.get(0));
    }
}
