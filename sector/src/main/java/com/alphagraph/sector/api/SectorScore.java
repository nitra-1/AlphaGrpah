package com.alphagraph.sector.api;

import com.alphagraph.common.engine.Score;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The Sector Engine's output for one sector, mirroring {@code sector.sector_scores}
 * (docs/003_Database_Architecture.md §3a). Same 0-100 scale choice as every prior Phase 1 engine
 * (Modules 1.5-1.7), matching the roadmap's own worked example ("Sector Score 94").
 * {@code value()} is {@code sectorScore}; {@code confidence()} reflects both metric availability
 * and {@code constituentCount} - a 1-constituent sector's breadth/participation are trivially
 * degenerate, not a real cross-sectional read, so confidence is capped lower for small sectors
 * regardless of how "clean" the underlying numbers look.
 */
public record SectorScore(
    UUID sectorId, String sectorName, LocalDate asOfDate,
    Leadership leadership, SectorMomentum momentum, Rotation rotation, MoneyFlow moneyFlow,
    double sectorScore, double confidence,
    Double relativeStrength, Double sectorPerformancePct, Double sectorVolumeRatio,
    Double breadthPct, Double participationPct, int constituentCount,
    int ruleSetVersion, Instant computedAt
) implements Score {

    @Override
    public double value() {
        return sectorScore;
    }

    @Override
    public double confidence() {
        return confidence;
    }
}
