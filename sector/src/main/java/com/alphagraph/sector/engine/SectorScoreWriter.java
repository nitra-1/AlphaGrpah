package com.alphagraph.sector.engine;

import com.alphagraph.sector.api.SectorScore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * Upserts on (sector_id, as_of_date) — idempotent, matching every other module's
 * Loader/ScoreWriter convention (docs/002_Engine_Architecture.md §5).
 */
@Component
public class SectorScoreWriter {

    private final JdbcTemplate jdbcTemplate;

    public SectorScoreWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void write(SectorScore score) {
        jdbcTemplate.update(
            """
            INSERT INTO sector.sector_scores
                (id, sector_id, sector_name, as_of_date, leadership, momentum, rotation, money_flow,
                 sector_score, confidence, relative_strength, sector_performance_pct, sector_volume_ratio,
                 breadth_pct, participation_pct, constituent_count, rule_set_version, computed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (sector_id, as_of_date) DO UPDATE SET
                sector_name = EXCLUDED.sector_name, leadership = EXCLUDED.leadership,
                momentum = EXCLUDED.momentum, rotation = EXCLUDED.rotation, money_flow = EXCLUDED.money_flow,
                sector_score = EXCLUDED.sector_score, confidence = EXCLUDED.confidence,
                relative_strength = EXCLUDED.relative_strength, sector_performance_pct = EXCLUDED.sector_performance_pct,
                sector_volume_ratio = EXCLUDED.sector_volume_ratio, breadth_pct = EXCLUDED.breadth_pct,
                participation_pct = EXCLUDED.participation_pct, constituent_count = EXCLUDED.constituent_count,
                rule_set_version = EXCLUDED.rule_set_version, computed_at = EXCLUDED.computed_at
            """,
            UUID.randomUUID(), score.sectorId(), score.sectorName(), score.asOfDate(), score.leadership().name(),
            score.momentum().name(), score.rotation().name(), score.moneyFlow().name(), score.sectorScore(),
            score.confidence(), score.relativeStrength(), score.sectorPerformancePct(), score.sectorVolumeRatio(),
            score.breadthPct(), score.participationPct(), score.constituentCount(), score.ruleSetVersion(),
            Timestamp.from(score.computedAt())
        );
    }
}
