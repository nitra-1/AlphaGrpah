package com.alphagraph.api.admin;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/** {@code parentSectorId} is optional - null means a top-level sector. */
public record SectorRequest(@NotBlank String name, UUID parentSectorId) {
}
