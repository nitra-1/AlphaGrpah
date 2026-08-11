package com.alphagraph.api.admin;

import com.alphagraph.reference.api.SectorDetail;

import java.util.UUID;

public record SectorDto(UUID id, String name, UUID parentSectorId, String parentName, long instrumentCount) {

    public static SectorDto from(SectorDetail detail) {
        return new SectorDto(detail.id(), detail.name(), detail.parentSectorId(), detail.parentName(), detail.instrumentCount());
    }
}
