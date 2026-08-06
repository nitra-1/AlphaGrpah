package com.alphagraph.api.dashboard;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Module 2.10: Decision Intelligence API Layer. One endpoint per named widget plus a combined
 * summary endpoint - every response is already-computed, dashboard-shaped JSON assembled from the
 * corporate module's own readers, per docs/004_API_Architecture.md §5. GET-only, authenticated
 * (any valid JWT, no role restriction), matching every other read endpoint's convention.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @Operation(summary = "Today's Biggest Orders", description = "New/tender-won orders in the last N days, largest value first.")
    @GetMapping("/biggest-orders")
    public List<BiggestOrderDto> biggestOrders(@RequestParam(defaultValue = "1") int days) {
        return service.biggestOrders(days);
    }

    @Operation(summary = "Corporate Events", description = "Detected corporate events across all tracked instruments in the last N days.")
    @GetMapping("/corporate-events")
    public List<CorporateEventDto> corporateEvents(@RequestParam(defaultValue = "7") int days) {
        return service.corporateEvents(days);
    }

    @Operation(summary = "Management Guidance Changes", description = "Forward-looking guidance statements across all tracked instruments in the last N days.")
    @GetMapping("/guidance-changes")
    public List<GuidanceChangeDto> guidanceChanges(@RequestParam(defaultValue = "7") int days) {
        return service.guidanceChanges(days);
    }

    @Operation(summary = "Positive News", description = "News items with a positive impact across all tracked instruments in the last N days.")
    @GetMapping("/positive-news")
    public List<NewsItemDto> positiveNews(@RequestParam(defaultValue = "7") int days) {
        return service.positiveNews(days);
    }

    @Operation(summary = "Negative News", description = "News items with a negative impact across all tracked instruments in the last N days.")
    @GetMapping("/negative-news")
    public List<NewsItemDto> negativeNews(@RequestParam(defaultValue = "7") int days) {
        return service.negativeNews(days);
    }

    @Operation(summary = "Top Catalysts", description = "Every tracked instrument's latest News Catalyst score, highest first.")
    @GetMapping("/top-catalysts")
    public List<TopCatalystDto> topCatalysts() {
        return service.topCatalysts();
    }

    @Operation(summary = "Growth Visibility", description = "Every tracked instrument's latest Management Commentary growth-visibility score, highest first.")
    @GetMapping("/growth-visibility")
    public List<GrowthVisibilityDto> growthVisibility() {
        return service.growthVisibility();
    }

    @Operation(summary = "Corporate Score", description = "Every tracked instrument's latest Corporate Score, highest first.")
    @GetMapping("/corporate-scores")
    public List<CorporateScoreDto> corporateScores() {
        return service.corporateScores();
    }

    @Operation(summary = "Full dashboard summary", description = "All widgets in one call, using each widget's default lookback window.")
    @GetMapping
    public DashboardSummaryDto summary() {
        return service.summary();
    }
}
