package com.alphagraph.reference.api;

import java.util.UUID;

/** One tracked instrument for a picker/dropdown - deliberately not scored data, just identity plus its current sector. */
public record TrackedInstrumentSummary(UUID id, String symbol, String name, UUID sectorId, String sectorName) {
}
