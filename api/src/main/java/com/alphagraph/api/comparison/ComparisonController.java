package com.alphagraph.api.comparison;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Module 3.5: Opportunity Comparison - any N instruments side by side across all six domain scores plus Swing/Long-Term Score, Rating, and Rank. Read-only, no role restriction beyond a valid JWT. */
@RestController
@RequestMapping("/api/v1/comparison")
public class ComparisonController {

    private final ComparisonService comparisonService;

    public ComparisonController(ComparisonService comparisonService) {
        this.comparisonService = comparisonService;
    }

    @Operation(summary = "Compare instruments side by side", description = "Every requested instrument's current domain scores and Swing/Long-Term Score/Rank - instruments with no score yet appear with null fields, unresolvable instrument ids are silently skipped.")
    @GetMapping
    public List<ComparisonEntryDto> compare(@RequestParam List<UUID> instrumentIds) {
        return comparisonService.compare(instrumentIds);
    }
}
