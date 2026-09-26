package com.alphagraph.api.admin;

import com.alphagraph.corporate.news.NewsDiscoveryService;
import com.alphagraph.corporate.relationships.EntityResolver;
import com.alphagraph.market.pricing.HistoricalBackfillService;
import com.alphagraph.ownership.deals.DiscoveryService;
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
    private final EntityResolver entityResolver;
    private final DiscoveryService discoveryService;
    private final NewsDiscoveryService newsDiscoveryService;

    public InstrumentAdditionService(
        SecurityMasterReader securityMasterReader, SectorService sectorService,
        InstrumentWriter instrumentWriter, HistoricalBackfillService backfillService, EntityResolver entityResolver,
        DiscoveryService discoveryService, NewsDiscoveryService newsDiscoveryService
    ) {
        this.securityMasterReader = securityMasterReader;
        this.sectorService = sectorService;
        this.instrumentWriter = instrumentWriter;
        this.backfillService = backfillService;
        this.entityResolver = entityResolver;
        this.discoveryService = discoveryService;
        this.newsDiscoveryService = newsDiscoveryService;
    }

    public InstrumentDto addInstrument(String symbol, String sectorName) {
        SecurityMasterEntry masterEntry = securityMasterReader.findBySymbol(symbol)
            .orElseThrow(() -> new IllegalArgumentException(
                "\"" + symbol + "\" isn't in NSE's listed-equity master - pick a symbol from the search results, don't type one"
            ));

        UUID sectorId = sectorService.findOrCreateByName(sectorName);

        UUID instrumentId = instrumentWriter.create(masterEntry.symbol(), masterEntry.companyName(), masterEntry.isin(), sectorId)
            .orElseThrow(() -> new IllegalArgumentException(symbol + " is already tracked"));

        // Without this, NewsExtractor/ManagementExtractor/OrderExtractor facts about this
        // instrument could never resolve to a tracked instrument (NewsInstrumentMatcher requires
        // linked_instrument_id) until a future one-off backfill catches up - the same gap that
        // left 51 of the 59 previously-tracked instruments unlinked, since only a one-time
        // migration ever populated this before.
        entityResolver.linkTrackedInstrument(instrumentId, masterEntry.symbol(), masterEntry.companyName());

        // No-op for a symbol that was never a Discovery candidate (no matching discovery_status
        // row) - only updates the admin's own status history for a symbol that actually was one;
        // DiscoveryReader.findPendingReview already excludes it live via reference.instruments,
        // regardless of this flag.
        discoveryService.markPromoted(masterEntry.symbol());

        // Same no-op-for-most-symbols reasoning as the discoveryService call above, but for a
        // genuinely separate root cause (news exposure, not bulk/block deals) - a symbol only has
        // a corporate.news_discovery_candidates row if it was once identified as exposed to a real
        // economic event.
        newsDiscoveryService.markPromoted(masterEntry.symbol());

        backfillService.backfillAsync(instrumentId, masterEntry.symbol());

        return new InstrumentDto(instrumentId, masterEntry.symbol(), masterEntry.companyName(), masterEntry.isin(), sectorName);
    }
}
