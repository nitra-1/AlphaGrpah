package com.alphagraph.api.admin;

import com.alphagraph.api.error.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * News & Economic Discovery - replaces the retired {@code NewsReviewController}'s manual
 * relevance-triage queue. Every article is now automatically classified into Economic Events ->
 * Sector Impacts -> Company Exposures (tracked and untracked) -> Discovery Candidates; this
 * controller is read-mostly, with only the candidate lifecycle's {@code observe}/{@code dismiss}
 * actions as mutations. No promote endpoint here, same convention as {@link DiscoveryController}:
 * promotion only ever happens through the existing, unmodified
 * {@code POST /api/v1/admin/instruments} flow.
 */
@RestController
@RequestMapping("/api/v1/admin/news-discovery")
@PreAuthorize("hasRole('ADMIN')")
public class NewsDiscoveryController {

    private final NewsDiscoveryViewService viewService;

    public NewsDiscoveryController(NewsDiscoveryViewService viewService) {
        this.viewService = viewService;
    }

    @Operation(summary = "The 5 stat-card numbers for the News & Economic Discovery dashboard")
    @GetMapping("/summary")
    public NewsDiscoverySummaryDto summary() {
        return viewService.summary();
    }

    @Operation(summary = "Latest Economic Events feed", description = "Most recently detected first.")
    @GetMapping("/events")
    public List<EconomicEventSummaryDto> events(@RequestParam(defaultValue = "20") int limit) {
        return viewService.latestEvents(limit);
    }

    @Operation(summary = "One event's full detail", description = "Sector impacts, company exposures (tracked/untracked split), and source articles.")
    @GetMapping("/events/{id}")
    public EconomicEventDetailDto event(@PathVariable UUID id) {
        return viewService.eventDetail(id).orElseThrow(() -> new NotFoundException("No economic event with id " + id));
    }

    @Operation(summary = "Sector Impact Map", description = "Real-time aggregation over non-expired sector impacts - one row per sector currently carrying a live impact.")
    @GetMapping("/sector-impact-map")
    public List<SectorImpactMapEntryDto> sectorImpactMap() {
        return viewService.sectorImpactMap();
    }

    @Operation(summary = "News-driven discovery candidates", description = "Companies recently identified as exposed to a meaningful economic event - tracked and untracked, no ranking/score.")
    @GetMapping("/candidates")
    public List<NewsDiscoveryCandidateDto> candidates() {
        return viewService.candidates();
    }

    @Operation(summary = "Mark a candidate as under observation", description = "An analyst is now watching this - distinct from promoting or dismissing it. Valid only from NEW.")
    @PostMapping("/candidates/{symbol}/observe")
    public ResponseEntity<Void> observe(@PathVariable String symbol) {
        if (!viewService.observe(symbol)) {
            throw new NotFoundException("No NEW news discovery candidate with symbol " + symbol);
        }
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Dismiss a candidate", description = "Terminal - stays visible for audit but no longer actionable.")
    @PostMapping("/candidates/{symbol}/dismiss")
    public ResponseEntity<Void> dismiss(@PathVariable String symbol) {
        if (!viewService.dismiss(symbol)) {
            throw new NotFoundException("No news discovery candidate with symbol " + symbol + " (or already dismissed)");
        }
        return ResponseEntity.noContent().build();
    }
}
