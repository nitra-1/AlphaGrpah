package com.alphagraph.api.admin;

import java.util.UUID;

public record SectorImpactMapEntryDto(UUID sectorId, String sectorName, String direction, String strength, int contributingEventCount) {
}
