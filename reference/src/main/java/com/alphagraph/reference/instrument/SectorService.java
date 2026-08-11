package com.alphagraph.reference.instrument;

import com.alphagraph.reference.api.SectorDetail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * reference.sectors has no unique constraint on name (confirmed the hard way at Module 1.8 -
 * V2's dummy tree already had a "Capital Goods" row, and a naive second insert made
 * "WHERE name = 'Capital Goods'" ambiguous) - {@code findOrCreateByName} always looks up first
 * and only inserts on a genuine miss, picking the oldest match if duplicates somehow exist rather
 * than crashing on ambiguity. The admin CRUD methods below ({@code create}/{@code update})
 * enforce case-insensitive uniqueness themselves, at the application layer rather than a DB
 * constraint, precisely because that constraint doesn't exist and adding one now would first
 * require resolving whatever duplicates already exist in a live database.
 */
@Component
public class SectorService {

    private final JdbcTemplate jdbcTemplate;

    public SectorService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Every sector with its parent's name and current instrument count, for the admin Sectors page. */
    public List<SectorDetail> listAllWithDetail() {
        return jdbcTemplate.query(
            """
            SELECT s.id, s.name, s.parent_sector_id, p.name AS parent_name,
                   (SELECT count(*) FROM reference.instruments i WHERE i.sector_id = s.id) AS instrument_count
            FROM reference.sectors s
            LEFT JOIN reference.sectors p ON p.id = s.parent_sector_id
            ORDER BY s.name
            """,
            (rs, rowNum) -> new SectorDetail(
                (UUID) rs.getObject("id"), rs.getString("name"),
                (UUID) rs.getObject("parent_sector_id"), rs.getString("parent_name"),
                rs.getLong("instrument_count")
            )
        );
    }

    public UUID findOrCreateByName(String name) {
        List<UUID> existing = jdbcTemplate.query(
            "SELECT id FROM reference.sectors WHERE name = ? ORDER BY created_at LIMIT 1",
            (rs, rowNum) -> (UUID) rs.getObject("id"), name
        );
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO reference.sectors (id, name) VALUES (?, ?)", id, name);
        return id;
    }

    /** @throws IllegalArgumentException if the name is blank, already taken (case-insensitive), or parentSectorId doesn't exist */
    public UUID create(String name, UUID parentSectorId) {
        String trimmedName = requireValidName(name);
        requireUniqueName(trimmedName, null);
        if (parentSectorId != null) {
            requireSectorExists(parentSectorId);
        }

        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO reference.sectors (id, name, parent_sector_id) VALUES (?, ?, ?)", id, trimmedName, parentSectorId
        );
        return id;
    }

    /**
     * @return false if no sector exists with this id
     * @throws IllegalArgumentException if the name is blank, taken by a different sector, the
     *     parent doesn't exist, the sector would become its own parent, or the new parent is one
     *     of the sector's own descendants (would create a cycle in the tree)
     */
    public boolean update(UUID id, String name, UUID parentSectorId) {
        if (!sectorExists(id)) {
            return false;
        }
        String trimmedName = requireValidName(name);
        requireUniqueName(trimmedName, id);
        if (parentSectorId != null) {
            if (parentSectorId.equals(id)) {
                throw new IllegalArgumentException("A sector cannot be its own parent");
            }
            requireSectorExists(parentSectorId);
            requireNoCycle(id, parentSectorId);
        }

        jdbcTemplate.update("UPDATE reference.sectors SET name = ?, parent_sector_id = ? WHERE id = ?", trimmedName, parentSectorId, id);
        return true;
    }

    /**
     * @return false if no sector exists with this id
     * @throws IllegalArgumentException if instruments are still assigned to this sector, or
     *     sub-sectors still exist under it - either would otherwise be silently orphaned
     */
    public boolean delete(UUID id) {
        if (!sectorExists(id)) {
            return false;
        }

        Long instrumentCount = jdbcTemplate.queryForObject("SELECT count(*) FROM reference.instruments WHERE sector_id = ?", Long.class, id);
        if (instrumentCount != null && instrumentCount > 0) {
            throw new IllegalArgumentException(instrumentCount + " instrument(s) are still assigned to this sector - reassign them first");
        }

        Long childCount = jdbcTemplate.queryForObject("SELECT count(*) FROM reference.sectors WHERE parent_sector_id = ?", Long.class, id);
        if (childCount != null && childCount > 0) {
            throw new IllegalArgumentException(childCount + " sub-sector(s) still exist under this sector - delete or reassign them first");
        }

        jdbcTemplate.update("DELETE FROM reference.sectors WHERE id = ?", id);
        return true;
    }

    private String requireValidName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Sector name is required");
        }
        return name.trim();
    }

    private void requireUniqueName(String name, UUID excludingId) {
        Long count = excludingId == null
            ? jdbcTemplate.queryForObject("SELECT count(*) FROM reference.sectors WHERE lower(name) = lower(?)", Long.class, name)
            : jdbcTemplate.queryForObject(
                "SELECT count(*) FROM reference.sectors WHERE lower(name) = lower(?) AND id <> ?", Long.class, name, excludingId
            );
        if (count != null && count > 0) {
            throw new IllegalArgumentException("A sector named '" + name + "' already exists");
        }
    }

    private boolean sectorExists(UUID id) {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM reference.sectors WHERE id = ?", Long.class, id);
        return count != null && count > 0;
    }

    private void requireSectorExists(UUID id) {
        if (!sectorExists(id)) {
            throw new IllegalArgumentException("No sector with id " + id);
        }
    }

    /** Walks up newParentId's own ancestor chain - if it ever reaches {@code id}, setting that parent would create a cycle. */
    private void requireNoCycle(UUID id, UUID newParentId) {
        UUID current = newParentId;
        while (current != null) {
            if (current.equals(id)) {
                throw new IllegalArgumentException("Cannot set that parent - it is a descendant of this sector, which would create a cycle");
            }
            current = jdbcTemplate.query(
                "SELECT parent_sector_id FROM reference.sectors WHERE id = ?",
                (rs, rowNum) -> (UUID) rs.getObject("parent_sector_id"), current
            ).stream().findFirst().orElse(null);
        }
    }
}
