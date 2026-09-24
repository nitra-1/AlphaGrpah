package com.alphagraph.api.opportunities;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The Stage 1-5 discovery pipeline's own product surface - every tracked instrument's current
 * lifecycle classification, and one instrument's full causal chain (evidence -> inflection ->
 * sequence -> convergence -> lifecycle). Read-only, no role restriction beyond a valid JWT, same
 * convention as {@code api.rankings.RankingsController}.
 */
@RestController
@RequestMapping("/api/v1/opportunities")
public class OpportunitiesController {

    private final OpportunitiesService opportunitiesService;

    public OpportunitiesController(OpportunitiesService opportunitiesService) {
        this.opportunitiesService = opportunitiesService;
    }

    @Operation(summary = "List every tracked instrument's current Stage 5 lifecycle classification")
    @GetMapping
    public List<OpportunityEntryDto> list() {
        return opportunitiesService.list();
    }

    @Operation(summary = "One instrument's full Stage 1-5 discovery detail", description = "Lifecycle + convergence + the Stage 1-3 causal chain per domain, anchored to the convergence snapshot's own date - 404 until a lifecycle classification exists for this instrument.")
    @GetMapping("/{instrumentId}")
    public OpportunityDetailDto detail(@PathVariable UUID instrumentId) {
        return opportunitiesService.detail(instrumentId);
    }
}
