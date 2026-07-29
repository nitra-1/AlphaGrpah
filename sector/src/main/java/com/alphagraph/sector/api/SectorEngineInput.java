package com.alphagraph.sector.api;

import java.util.List;
import java.util.UUID;

/**
 * The Sector Engine's input: one sector's constituents, plus every tracked instrument (used only
 * as the market-wide baseline for Relative Strength - constituents already in
 * {@code sectorConstituents} are not excluded from {@code allTrackedInstruments}, the engine
 * itself doesn't need them removed since relative strength compares the sector's average against
 * the whole market's average, sector included).
 */
public record SectorEngineInput(
    UUID sectorId, String sectorName,
    List<SectorConstituentInput> sectorConstituents,
    List<SectorConstituentInput> allTrackedInstruments
) {
    public SectorEngineInput {
        sectorConstituents = List.copyOf(sectorConstituents);
        allTrackedInstruments = List.copyOf(allTrackedInstruments);
    }
}
