package com.alphagraph.sector.engine;

import com.alphagraph.sector.api.Leadership;
import com.alphagraph.sector.api.MoneyFlow;
import com.alphagraph.sector.api.Rotation;
import com.alphagraph.sector.api.SectorMomentum;
import com.alphagraph.sector.api.SectorScore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads {@link SectorScore}s back - added in Module 2.9 (AI Analyst, corporate module), the first
 * consumer to need one ({@link SectorScoreWriter} was write-only until now). A claim like "Defence
 * sector remains strongest" needs to compare one sector's score against every other sector's, not
 * just read it in isolation - {@link #findAllLatest} is what makes that comparison possible.
 */
@Component
public class SectorScoreReader {

    private static final RowMapper<SectorScore> ROW_MAPPER = (rs, rowNum) -> new SectorScore(
        (UUID) rs.getObject("sector_id"), rs.getString("sector_name"), rs.getDate("as_of_date").toLocalDate(),
        Leadership.valueOf(rs.getString("leadership")), SectorMomentum.valueOf(rs.getString("momentum")),
        Rotation.valueOf(rs.getString("rotation")), MoneyFlow.valueOf(rs.getString("money_flow")),
        rs.getDouble("sector_score"), rs.getDouble("confidence"),
        rs.getObject("relative_strength") == null ? null : rs.getDouble("relative_strength"),
        rs.getObject("sector_performance_pct") == null ? null : rs.getDouble("sector_performance_pct"),
        rs.getObject("sector_volume_ratio") == null ? null : rs.getDouble("sector_volume_ratio"),
        rs.getObject("breadth_pct") == null ? null : rs.getDouble("breadth_pct"),
        rs.getObject("participation_pct") == null ? null : rs.getDouble("participation_pct"),
        rs.getInt("constituent_count"), rs.getInt("rule_set_version"), rs.getTimestamp("computed_at").toInstant()
    );

    private static final String SELECT_COLUMNS = """
        sector_id, sector_name, as_of_date, leadership, momentum, rotation, money_flow, sector_score,
        confidence, relative_strength, sector_performance_pct, sector_volume_ratio, breadth_pct,
        participation_pct, constituent_count, rule_set_version, computed_at
        """;

    private final JdbcTemplate jdbcTemplate;

    public SectorScoreReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<SectorScore> findLatest(UUID sectorId) {
        List<SectorScore> rows = jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " FROM sector.sector_scores WHERE sector_id = ? ORDER BY as_of_date DESC LIMIT 1",
            ROW_MAPPER, sectorId
        );
        return rows.stream().findFirst();
    }

    private static final String SELECT_COLUMNS_FOR_INSTRUMENT = """
        SELECT ss.sector_id, ss.sector_name, ss.as_of_date, ss.leadership, ss.momentum, ss.rotation,
               ss.money_flow, ss.sector_score, ss.confidence, ss.relative_strength, ss.sector_performance_pct,
               ss.sector_volume_ratio, ss.breadth_pct, ss.participation_pct, ss.constituent_count,
               ss.rule_set_version, ss.computed_at
        FROM sector.sector_scores ss
        JOIN reference.instruments i ON i.sector_id = ss.sector_id
        """;

    /** The sector a tracked instrument belongs to, via reference.instruments.sector_id - by value, no cross-schema FK. */
    public Optional<SectorScore> findLatestForInstrument(UUID instrumentId) {
        List<SectorScore> rows = jdbcTemplate.query(
            SELECT_COLUMNS_FOR_INSTRUMENT + " WHERE i.id = ? ORDER BY ss.as_of_date DESC LIMIT 1",
            ROW_MAPPER, instrumentId
        );
        return rows.stream().findFirst();
    }

    /** Same as {@link #findLatestForInstrument}, but the latest score with as_of_date on or before {@code asOfDate} - used to resolve what a decision made on that date could actually have known. */
    public Optional<SectorScore> findAsOfForInstrument(UUID instrumentId, LocalDate asOfDate) {
        List<SectorScore> rows = jdbcTemplate.query(
            SELECT_COLUMNS_FOR_INSTRUMENT + " WHERE i.id = ? AND ss.as_of_date <= ? ORDER BY ss.as_of_date DESC LIMIT 1",
            ROW_MAPPER, instrumentId, Date.valueOf(asOfDate)
        );
        return rows.stream().findFirst();
    }

    /** Every sector's latest score, one row per sector - the input to a cross-sector ranking. */
    public List<SectorScore> findAllLatest() {
        return jdbcTemplate.query(
            "SELECT DISTINCT ON (sector_id) " + SELECT_COLUMNS +
            " FROM sector.sector_scores ORDER BY sector_id, as_of_date DESC",
            ROW_MAPPER
        );
    }
}
