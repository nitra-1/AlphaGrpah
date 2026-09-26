package com.alphagraph.corporate.api;

import java.util.UUID;

/** One {@code corporate.economic_event_sector_impacts} row - always read alongside its parent event, never standalone. */
public record SectorImpactDetail(
    UUID sectorId, String sectorName, String direction, String strength, double confidence, String mechanism
) {
}
