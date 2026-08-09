package com.alphagraph.api.admin;

import com.alphagraph.market.pricing.HistoricalBackfillService;
import com.alphagraph.reference.api.SecurityMasterEntry;
import com.alphagraph.reference.instrument.InstrumentWriter;
import com.alphagraph.reference.instrument.SectorService;
import com.alphagraph.reference.securitymaster.SecurityMasterReader;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Orchestrates "Add Instrument" - the only place this cross-module flow can live, since
 * reference.instrument/reference.securitymaster/market.pricing never depend on each other
 * directly (docs/001_System_Architecture.md §4 Rule 3). Company name and ISIN are never taken
 * from the request - only {@code symbol} is client-supplied, re-verified server-side against
 * reference.security_master, and every other field comes from that lookup. This is the whole
 * point of building the security master (docs/006_Universe_Expansion_Runbook.md's original
 * manual process required typing and independently verifying an ISIN by hand).
 */
@Service
public class InstrumentAdditionService {

    private final SecurityMasterReader securityMasterReader;
    private final SectorService sectorService;
    private final InstrumentWriter instrumentWriter;
    private final HistoricalBackfillService backfillService;

    public InstrumentAdditionService(
        SecurityMasterReader securityMasterReader, SectorService sectorService,
        InstrumentWriter instrumentWriter, HistoricalBackfillService backfillService
    ) {
        this.securityMasterReader = securityMasterReader;
        this.sectorService = sectorService;
        this.instrumentWriter = instrumentWriter;
        this.backfillService = backfillService;
    }

    public InstrumentDto addInstrument(String symbol, String sectorName) {
        SecurityMasterEntry masterEntry = securityMasterReader.findBySymbol(symbol)
            .orElseThrow(() -> new IllegalArgumentException(
                "\"" + symbol + "\" isn't in NSE's listed-equity master - pick a symbol from the search results, don't type one"
            ));

        UUID sectorId = sectorService.findOrCreateByName(sectorName);

        UUID instrumentId = instrumentWriter.create(masterEntry.symbol(), masterEntry.companyName(), masterEntry.isin(), sectorId)
            .orElseThrow(() -> new IllegalArgumentException(symbol + " is already tracked"));

        backfillService.backfillAsync(instrumentId, masterEntry.symbol());

        return new InstrumentDto(instrumentId, masterEntry.symbol(), masterEntry.companyName(), masterEntry.isin(), sectorName);
    }
}
