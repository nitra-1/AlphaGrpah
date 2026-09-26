package com.alphagraph.api.admin;

import java.util.UUID;

public record SectorImpactDetailDto(UUID sectorId, String sectorName, String direction, String strength, double confidence, String mechanism) {
}
