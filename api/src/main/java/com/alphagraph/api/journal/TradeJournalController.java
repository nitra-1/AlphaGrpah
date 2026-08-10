package com.alphagraph.api.journal;

import com.alphagraph.api.security.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final OutcomeTrackingService outcomeTrackingService;

    public TradeJournalController(TradeJournalViewService viewService, OutcomeTrackingService outcomeTrackingService) {
        this.viewService = viewService;
        this.outcomeTrackingService = outcomeTrackingService;
    }

    @Operation(summary = "List every trade", description = "Every executed buy/sell, newest first.")
    @GetMapping
    public List<TradeJournalEntryDto> list(@AuthenticationPrincipal JwtService.AuthenticatedPrincipal principal) {
        return viewService.list(principal.userId());
    }

    @Operation(summary = "Outcome tracking (Module 3.9)", description = "Aggregate outcomes across every closed trade - total realized P&L, win rate, average win/loss, best/worst trade, per-instrument breakdown.")
    @GetMapping("/outcomes")
    public OutcomeSummaryDto outcomes(@AuthenticationPrincipal JwtService.AuthenticatedPrincipal principal) {
        return outcomeTrackingService.compute(principal.userId());
    }

    @Operation(summary = "List trades for one instrument", description = "Every executed buy/sell for one instrument, newest first.")
    @GetMapping("/{instrumentId}")
    public List<TradeJournalEntryDto> byInstrument(
        @AuthenticationPrincipal JwtService.AuthenticatedPrincipal principal, @PathVariable UUID instrumentId
    ) {
        return viewService.listByInstrument(principal.userId(), instrumentId);
    }
}
