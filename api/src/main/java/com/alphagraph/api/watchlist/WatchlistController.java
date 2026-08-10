package com.alphagraph.api.watchlist;

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
 * Module 3.2, retrofitted for multi-tenancy (decision V7): each caller's own watchlist. Every
 * endpoint here just needs a valid JWT - add/remove are NOT ADMIN-gated, matching
 * api.portfolio.PortfolioController's own retrofit for the same reason: a USER account's whole
 * point is a personal Watchlist, so gating mutations behind ADMIN would defeat it. (Same bug,
 * caught the same way - live two-user E2E testing.)
 */
@RestController
@RequestMapping("/api/v1/watchlist")
public class WatchlistController {

    private final WatchlistViewService viewService;

    public WatchlistController(WatchlistViewService viewService) {
        this.viewService = viewService;
    }

    @Operation(summary = "List the watchlist", description = "Every watched instrument with its current Swing/Long-Term Score and Rank, if computed yet.")
    @GetMapping
    public List<WatchlistEntryDto> list(@AuthenticationPrincipal JwtService.AuthenticatedPrincipal principal) {
        return viewService.list(principal.userId());
    }

    @Operation(summary = "Add an instrument to the watchlist", description = "No-op if already on the list.")
    @PostMapping
    public ResponseEntity<WatchlistEntryDto> add(
        @AuthenticationPrincipal JwtService.AuthenticatedPrincipal principal, @Valid @RequestBody AddWatchlistItemRequest request
    ) {
        WatchlistEntryDto entry = viewService.add(principal.userId(), request.instrumentId())
            .orElseThrow(() -> new NotFoundException("No instrument with id " + request.instrumentId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(entry);
    }

    @Operation(summary = "Remove an instrument from the watchlist")
    @DeleteMapping("/{instrumentId}")
    public ResponseEntity<Void> remove(
        @AuthenticationPrincipal JwtService.AuthenticatedPrincipal principal, @PathVariable UUID instrumentId
    ) {
        if (!viewService.remove(principal.userId(), instrumentId)) {
            throw new NotFoundException("Instrument " + instrumentId + " is not on the watchlist");
        }
        return ResponseEntity.noContent().build();
    }
}
