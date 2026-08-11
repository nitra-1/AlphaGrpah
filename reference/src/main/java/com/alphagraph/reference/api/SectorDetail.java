package com.alphagraph.reference.api;

import java.util.UUID;

/** One sector for the admin Sectors page - parentName/instrumentCount are display-only, joined for a single list call rather than N+1 lookups. */
public record SectorDetail(UUID id, String name, UUID parentSectorId, String parentName, long instrumentCount) {
}
