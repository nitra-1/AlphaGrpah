package com.alphagraph.api.admin;

import java.util.UUID;

public record InstrumentDto(UUID id, String symbol, String companyName, String isin, String sectorName) {
}
