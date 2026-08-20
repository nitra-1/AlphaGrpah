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

    /**
     * Creates (or retroactively links) the {@code knowledge.entity_master} row for a newly-added
     * tracked instrument - the ongoing counterpart to {@code V9__init_knowledge_relationship_engine
     * .sql}'s one-time backfill, which only ever linked the instruments tracked at the moment that
     * migration ran. Every instrument added afterward had no linked entity at all until this
     * method existed - {@code NewsInstrumentMatcher} (which requires {@code linked_instrument_id
     * IS NOT NULL}) could never match news naming it, and {@code RelationshipBuilder} would create
     * a second, orphaned entity the first time any extractor mentioned it. Called once, synchronously,
     * from {@code api.admin.InstrumentAdditionService} right after the instrument itself is created.
     *
     * <p>{@code canonical_name} is the trading symbol and {@code companyName} becomes its first
     * alias - the exact same convention the original seed used, so a document referring to either
     * "INFY" or "Infosys Limited" resolves to the same row. {@code ON CONFLICT (canonical_name) DO
     * UPDATE} handles both the fresh-instrument case and the case where an extractor already
     * created an unlinked entity under this exact symbol before it became tracked - either way,
     * the row ends up linked, never duplicated (the unique constraint would reject a duplicate
     * insert regardless).
     */
    public void linkTrackedInstrument(UUID instrumentId, String symbol, String companyName) {
        jdbcTemplate.update(
            """
            INSERT INTO knowledge.entity_master (entity_type, canonical_name, aliases, linked_instrument_id)
            VALUES ('COMPANY', ?, ARRAY[?]::text[], ?)
            ON CONFLICT (canonical_name) DO UPDATE SET linked_instrument_id = EXCLUDED.linked_instrument_id
            """,
            symbol, companyName, instrumentId
        );
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
