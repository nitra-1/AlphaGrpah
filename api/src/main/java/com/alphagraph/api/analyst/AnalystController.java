package com.alphagraph.api.analyst;

import com.alphagraph.decision.analyst.AiAnalystService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Module 3.7: the AI Analyst's first HTTP exposure - Module 2.9 built the service but never a
 * controller for it. Read-only, no role restriction beyond a valid JWT; each call is a real
 * Claude API call (not persisted, unlike Module 3.6's scheduled Daily Report), so this is
 * naturally lower-traffic than every other GET endpoint in this project.
 */
@RestController
@RequestMapping("/api/v1/analyst")
public class AnalystController {

    private final AiAnalystService aiAnalystService;

    public AnalystController(AiAnalystService aiAnalystService) {
        this.aiAnalystService = aiAnalystService;
    }

    @Operation(summary = "Explain a Corporate Score change", description = "\"Why has this instrument's Corporate Score changed?\" - grounded entirely in deterministic facts.")
    @GetMapping("/{instrumentId}/score-explanation")
    public AnalystExplanationDto scoreExplanation(@PathVariable UUID instrumentId) {
        return new AnalystExplanationDto(instrumentId, "SCORE_CHANGE", aiAnalystService.explainScoreChange(instrumentId));
    }

    @Operation(summary = "Explain a Swing Rank change", description = "\"Why moved from Rank 18 to Rank 5?\" - the roadmap's own worked example, grounded entirely in deterministic facts.")
    @GetMapping("/{instrumentId}/rank-explanation")
    public AnalystExplanationDto rankExplanation(@PathVariable UUID instrumentId) {
        return new AnalystExplanationDto(instrumentId, "RANK_CHANGE", aiAnalystService.explainRankChange(instrumentId));
    }
}
