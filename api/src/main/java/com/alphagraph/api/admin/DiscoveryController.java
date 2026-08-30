package com.alphagraph.api.admin;

import com.alphagraph.api.error.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Sprint 1 of the bulk/block deal auto-discovery roadmap: symbols with real NSE bulk/block deal
 * activity AlphaGraph doesn't track yet, surfaced for admin review instead of being silently
 * discarded (see {@code ownership.deals.BulkDealsNormalizer}/{@code DiscoveredDealWriter}).
 * ADMIN-only throughout, matching every other mutation endpoint's convention in this project.
 *
 * <p>No promote endpoint here - promotion goes through the existing, unmodified
 * {@code POST /api/v1/admin/instruments} ({@link InstrumentController}); a symbol simply stops
 * appearing here once it exists in {@code reference.instruments} (checked live, not tracked by a
 * status flag this feature writes).
 */
@RestController
@RequestMapping("/api/v1/admin/discovery")
@PreAuthorize("hasRole('ADMIN')")
public class DiscoveryController {

    private final DiscoveryViewService viewService;

    public DiscoveryController(DiscoveryViewService viewService) {
        this.viewService = viewService;
    }

    @Operation(summary = "List untracked symbols with real bulk/block deal activity, awaiting admin review")
    @GetMapping
    public List<DiscoveryCandidateDto> list() {
        return viewService.listPendingReview();
    }

    @Operation(summary = "List individual deals for one Discovery symbol", description = "Expand-on-click detail - each deal's own materiality result is included when it's been scored yet, null otherwise.")
    @GetMapping("/{symbol}/deals")
    public List<DiscoveredDealDetailDto> deals(@PathVariable String symbol) {
        return viewService.listDealsForSymbol(symbol);
    }

    @Operation(summary = "Get the latest institutional interpretation for one Discovery symbol", description = "Event structure, institutional state, Discovery confirmation, confidence, and the reason codes backing them - 404 until this symbol's first interpretation has run.")
    @GetMapping("/{symbol}/interpretation")
    public InstitutionalInterpretationDetailDto interpretation(@PathVariable String symbol) {
        return viewService.findInterpretation(symbol)
            .orElseThrow(() -> new NotFoundException("No interpretation yet for symbol " + symbol));
    }

    @Operation(summary = "Dismiss a discovery candidate", description = "Terminal - the symbol stops appearing here even if new deal activity arrives later.")
    @PostMapping("/{symbol}/discard")
    public ResponseEntity<Void> discard(@PathVariable String symbol) {
        if (!viewService.discard(symbol)) {
            throw new NotFoundException("No pending discovery candidate with symbol " + symbol);
        }
        return ResponseEntity.noContent().build();
    }
}
