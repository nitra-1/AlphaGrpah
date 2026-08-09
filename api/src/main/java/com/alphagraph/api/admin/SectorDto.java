package com.alphagraph.api.admin;

import com.alphagraph.reference.api.SectorSummary;

import java.util.UUID;

public record SectorDto(UUID id, String name) {

    public static SectorDto from(SectorSummary summary) {
        return new SectorDto(summary.id(), summary.name());
    }
}
