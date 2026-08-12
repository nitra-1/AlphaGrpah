package com.alphagraph.api.learning;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Phase 4A.10: Learning Dashboard (limited) - evidence-accumulation stats and, once enough exists, whether AlphaGraph's own ratings have been directionally correct. No Pattern Mining/Weight Optimizer/Probability Engine content - none of that exists yet (Phase 4B, gated on data sufficiency). Any authenticated user, not admin-only - this is product transparency about whether the platform's own engine works, same audience as the main Dashboard. */
@RestController
@RequestMapping("/api/v1/learning")
public class LearningController {

    private final LearningService service;

    public LearningController(LearningService service) {
        this.service = service;
    }

    @Operation(summary = "Learning evidence summary", description = "How much decision-history/outcome data has accumulated, and (once a real sample size exists) whether AlphaGraph's own swing/long-term ratings have been directionally correct, by rating and horizon.")
    @GetMapping("/evidence-summary")
    public LearningEvidenceSummaryDto evidenceSummary() {
        return service.evidenceSummary();
    }
}
