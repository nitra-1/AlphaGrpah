package com.alphagraph.sector.api;

import java.util.List;
import java.util.UUID;

/** One sector constituent's recent bar history, ascending by trade date. */
public record SectorConstituentInput(UUID instrumentId, String symbol, List<SectorBar> barsAscending) {

    public SectorConstituentInput {
        barsAscending = List.copyOf(barsAscending);
    }
}
