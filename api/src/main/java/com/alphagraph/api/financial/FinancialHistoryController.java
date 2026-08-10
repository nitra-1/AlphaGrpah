package com.alphagraph.api.financial;

import com.alphagraph.financial.engine.FinancialResultReader;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Read-only view onto {@code financial.financial_results} - every period reported for one
 * instrument, oldest data source (bundled sample, manual research, or {@code
 * corporate.knowledge.FinancialResultsExtractor}) all landing in the same table with no
 * provenance distinction. Answers a real gap: until this endpoint, the only place raw Sales/PAT/
 * EPS figures appeared anywhere in this system was the Add Financial Data admin form's write
 * path - there was no way to see what had actually been extracted/entered without querying
 * Postgres directly. Newest period first, matching Trade Journal's own newest-first convention.
 * No role restriction beyond a valid JWT, matching every other read endpoint.
 */
@RestController
@RequestMapping("/api/v1/financial-results")
public class FinancialHistoryController {

    private final FinancialResultReader reader;

    public FinancialHistoryController(FinancialResultReader reader) {
        this.reader = reader;
    }

    @Operation(summary = "Financial history for one instrument", description = "Every reported period's Sales/PAT/EPS and balance-sheet figures, newest first.")
    @GetMapping("/{instrumentId}")
    public List<FinancialHistoryEntryDto> byInstrument(@PathVariable UUID instrumentId) {
        return reader.findPeriods(instrumentId).stream()
            .map(FinancialHistoryEntryDto::from)
            .sorted(Comparator.comparing(FinancialHistoryEntryDto::periodEnd).reversed())
            .toList();
    }
}
