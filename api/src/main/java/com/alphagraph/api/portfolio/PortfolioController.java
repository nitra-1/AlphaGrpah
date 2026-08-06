package com.alphagraph.api.portfolio;

import com.alphagraph.api.error.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Module 3.3: the single global portfolio - current holdings only (weighted-average cost basis),
 * enriched with live price/P&L and Rank/Score/Risk. No brokerage integration exists, so buy/sell
 * are entered manually via these endpoints. GET needs no role beyond a valid JWT; mutations
 * require ADMIN, matching every other mutation endpoint's convention (api.rule, api.watchlist).
 */
@RestController
@RequestMapping("/api/v1/portfolio")
public class PortfolioController {

    private final PortfolioViewService viewService;
    private final PortfolioRiskService riskService;

    public PortfolioController(PortfolioViewService viewService, PortfolioRiskService riskService) {
        this.viewService = viewService;
        this.riskService = riskService;
    }

    @Operation(summary = "List the portfolio", description = "Every current holding with live price, unrealized P&L, and current Swing/Long-Term Score, Rank, and Risk.")
    @GetMapping
    public List<PortfolioEntryDto> list() {
        return viewService.list();
    }

    @Operation(summary = "Portfolio-wide risk (Module 3.4)", description = "Market-value-weighted Risk Score plus single-holding and sector concentration - aggregate risk across the whole portfolio, distinct from each holding's own risk level.")
    @GetMapping("/risk")
    public PortfolioRiskDto risk() {
        return riskService.compute();
    }

    @Operation(summary = "Buy into a position", description = "Creates the holding, or recalculates its weighted-average cost basis if one already exists.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/buy")
    public ResponseEntity<PortfolioEntryDto> buy(@Valid @RequestBody BuyRequest request) {
        PortfolioEntryDto entry = viewService.buy(request.instrumentId(), request.quantity(), request.price())
            .orElseThrow(() -> new NotFoundException("No instrument with id " + request.instrumentId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(entry);
    }

    @Operation(summary = "Sell from a position", description = "Reduces the holding; the position is removed entirely once its quantity reaches zero. Errors if the quantity exceeds what's held.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/sell")
    public PortfolioEntryDto sell(@Valid @RequestBody SellRequest request) {
        return viewService.sell(request.instrumentId(), request.quantity())
            .orElseThrow(() -> new NotFoundException("No holding for instrument " + request.instrumentId()));
    }
}
