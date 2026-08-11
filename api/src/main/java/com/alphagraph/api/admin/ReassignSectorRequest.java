package com.alphagraph.api.admin;

import java.util.UUID;

/** {@code sectorId} is nullable - clears the instrument's sector rather than requiring one. */
public record ReassignSectorRequest(UUID sectorId) {
}
