package com.alphagraph.corporate.api;

import java.util.UUID;

/**
 * One row of the Sector Impact Map - a real-time aggregation over non-expired {@code
 * economic_event_sector_impacts}, computed at read time (same "LEFT JOIN LATERAL, no materialized
 * table" convention as {@code api.admin.CronMonitoringRepository}), never a stored table.
 * {@code direction}/{@code strength} are the single strongest still-live impact for this sector,
 * not an average - {@code contributingEventCount} is how many distinct events feed into it.
 */
public record SectorImpactMapEntry(
    UUID sectorId, String sectorName, String direction, String strength, int contributingEventCount
) {
}
