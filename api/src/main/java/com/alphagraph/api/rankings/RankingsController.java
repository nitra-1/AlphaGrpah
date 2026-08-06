package com.alphagraph.api.rankings;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Every tracked instrument's current Swing/Long-Term Rank - the frontend's landing view onto Module 3.1. Read-only, no role restriction beyond a valid JWT. */
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingsController {

    private final RankingsService rankingsService;

    public RankingsController(RankingsService rankingsService) {
        this.rankingsService = rankingsService;
    }

    @Operation(summary = "List every tracked instrument's current Rank", description = "Highest Swing Score (best Rank) first.")
    @GetMapping
    public List<RankingEntryDto> list() {
        return rankingsService.list();
    }
}
