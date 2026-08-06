package com.alphagraph.api.journal;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Module 3.8: the Trade Journal - read-only. There is deliberately no POST endpoint here; every
 * entry is auto-recorded as a byproduct of api.portfolio.PortfolioController's buy/sell (Module
 * 3.3), never entered independently. No role restriction beyond a valid JWT.
 */
@RestController
@RequestMapping("/api/v1/trade-journal")
public class TradeJournalController {

    private final TradeJournalViewService viewService;

    public TradeJournalController(TradeJournalViewService viewService) {
        this.viewService = viewService;
    }

    @Operation(summary = "List every trade", description = "Every executed buy/sell, newest first.")
    @GetMapping
    public List<TradeJournalEntryDto> list() {
        return viewService.list();
    }

    @Operation(summary = "List trades for one instrument", description = "Every executed buy/sell for one instrument, newest first.")
    @GetMapping("/{instrumentId}")
    public List<TradeJournalEntryDto> byInstrument(@PathVariable UUID instrumentId) {
        return viewService.listByInstrument(instrumentId);
    }
}
