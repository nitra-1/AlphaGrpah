package com.alphagraph.api.admin;

import com.alphagraph.financial.api.FinancialResult;
import com.alphagraph.financial.results.FinancialInstrumentLookup;
import com.alphagraph.financial.results.FinancialResultsLoader;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Manual data entry for financial.financial_results - the non-technical-form counterpart to the
 * bundled-CSV pipeline every prior instrument's fundamentals came through (there is still no free
 * live bulk source for this data, per docs/006_Universe_Expansion_Runbook.md §4 - this doesn't
 * change that, it only removes the need to edit a CSV file and restart the backend). Reuses the
 * exact same FinancialInstrumentLookup/FinancialResultsLoader the CSV pipeline already uses, so a
 * form submission and a CSV row take the identical path into the database.
 */
@RestController
@RequestMapping("/api/v1/admin/financial-results")
@PreAuthorize("hasRole('ADMIN')")
public class FinancialResultsAdminController {

    private final FinancialInstrumentLookup instrumentLookup;
    private final FinancialResultsLoader loader;

    public FinancialResultsAdminController(FinancialInstrumentLookup instrumentLookup, FinancialResultsLoader loader) {
        this.instrumentLookup = instrumentLookup;
        this.loader = loader;
    }

    @Operation(summary = "Record one period's financial results for a tracked instrument", description = "Upserts on (instrument, period, period type) - safe to re-submit to correct a figure.")
    @PostMapping
    public ResponseEntity<FinancialResult> create(@Valid @RequestBody AddFinancialResultRequest request) {
        UUID instrumentId = instrumentLookup.findIdBySymbol(request.symbol())
            .orElseThrow(() -> new IllegalArgumentException(request.symbol() + " isn't a tracked instrument"));

        FinancialResult result = new FinancialResult(
            instrumentId, request.symbol(), request.periodEnd(), request.periodType(),
            request.sales(), request.pat(), request.eps(),
            request.roePercentage(), request.rocePercentage(),
            request.operatingMarginPercentage(), request.netMarginPercentage(),
            request.cashFlowFromOperations(),
            request.totalAssets(), request.currentAssets(), request.currentLiabilities(),
            request.totalDebt(), request.totalEquity(), request.interestExpense(), request.ebit()
        );
        loader.load(result);

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}
