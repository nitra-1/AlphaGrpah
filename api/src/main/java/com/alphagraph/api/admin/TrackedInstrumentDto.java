package com.alphagraph.api.admin;

import com.alphagraph.reference.api.TrackedInstrumentSummary;

import java.util.UUID;

public record TrackedInstrumentDto(UUID id, String symbol, String name, UUID sectorId, String sectorName) {

    public static TrackedInstrumentDto from(TrackedInstrumentSummary summary) {
        return new TrackedInstrumentDto(summary.id(), summary.symbol(), summary.name(), summary.sectorId(), summary.sectorName());
    }
}
