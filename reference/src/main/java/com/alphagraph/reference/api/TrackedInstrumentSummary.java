package com.alphagraph.reference.api;

import java.util.UUID;

/** One tracked instrument for a picker/dropdown - deliberately not scored data, just identity. */
public record TrackedInstrumentSummary(UUID id, String symbol, String name) {
}
