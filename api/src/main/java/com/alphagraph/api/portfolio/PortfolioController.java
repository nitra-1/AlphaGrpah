package com.alphagraph.api.portfolio;

import com.alphagraph.api.error.NotFoundException;
import com.alphagraph.api.security.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Module 3.3, retrofitted for multi-tenancy (decision V7): each caller's own portfolio - current
 * holdings only (weighted-average cost basis), enriched with live price/P&L and Rank/Score/Risk.
 * No brokerage integration exists, so buy/sell are entered manually via these endpoints. Every
 * endpoint here just needs a valid JWT - unlike api.admin's endpoints, buy/sell are NOT
 * ADMIN-gated: a USER account's whole point is to have its own personal Portfolio, so gating
 * mutations behind ADMIN would make the USER role pointless. (An earlier draft of this controller
 * carried over the pre-multi-tenancy "mutations require ADMIN" convention by mistake - caught via
 * live two-user E2E testing when a USER's buy returned 403.)
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
    public List<PortfolioEntryDto> list(@AuthenticationPrincipal JwtService.AuthenticatedPrincipal principal) {
        return viewService.list(principal.userId());
    }

    @Operation(summary = "Portfolio-wide risk (Module 3.4)", description = "Market-value-weighted Risk Score plus single-holding and sector concentration - aggregate risk across the whole portfolio, distinct from each holding's own risk level.")
    @GetMapping("/risk")
    public PortfolioRiskDto risk(@AuthenticationPrincipal JwtService.AuthenticatedPrincipal principal) {
        return riskService.compute(principal.userId());
    }

    @Operation(summary = "Buy into a position", description = "Creates the holding, or recalculates its weighted-average cost basis if one already exists. Auto-records a BUY entry in the Trade Journal (Module 3.8).")
    @PostMapping("/buy")
    public ResponseEntity<PortfolioEntryDto> buy(
        @AuthenticationPrincipal JwtService.AuthenticatedPrincipal principal, @Valid @RequestBody BuyRequest request
    ) {
        PortfolioEntryDto entry = viewService.buy(principal.userId(), request.instrumentId(), request.quantity(), request.price(), request.rationale())
            .orElseThrow(() -> new NotFoundException("No instrument with id " + request.instrumentId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(entry);
    }

    @Operation(summary = "Sell from a position", description = "Reduces the holding; the position is removed entirely once its quantity reaches zero. Errors if the quantity exceeds what's held. Auto-records a SELL entry (with realized P&L) in the Trade Journal (Module 3.8).")
    @PostMapping("/sell")
    public PortfolioEntryDto sell(
        @AuthenticationPrincipal JwtService.AuthenticatedPrincipal principal, @Valid @RequestBody SellRequest request
    ) {
        return viewService.sell(principal.userId(), request.instrumentId(), request.quantity(), request.price(), request.rationale())
            .orElseThrow(() -> new NotFoundException("No holding for instrument " + request.instrumentId()));
    }

    @Operation(
        summary = "Delete a holding",
        description = "Removes the holding entirely without recording a trade - for correcting placeholder/test data, not a real exit. No P&L is computed and no Trade Journal entry is written. To record an actual sale, use POST /sell instead."
    )
    @DeleteMapping("/{instrumentId}")
    public ResponseEntity<Void> remove(
        @AuthenticationPrincipal JwtService.AuthenticatedPrincipal principal, @PathVariable UUID instrumentId
    ) {
        if (!viewService.remove(principal.userId(), instrumentId)) {
            throw new NotFoundException("No holding for instrument " + instrumentId);
        }
        return ResponseEntity.noContent().build();
    }
}
