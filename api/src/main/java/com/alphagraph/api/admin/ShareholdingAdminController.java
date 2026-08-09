package com.alphagraph.api.admin;

import com.alphagraph.ownership.api.ShareholdingPattern;
import com.alphagraph.ownership.pattern.OwnershipInstrumentLookup;
import com.alphagraph.ownership.pattern.ShareholdingLoader;
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

/** Manual data-entry counterpart to ShareholdingAdminController's financial-results sibling - same reasoning, reuses the same OwnershipInstrumentLookup/ShareholdingLoader the CSV pipeline uses. */
@RestController
@RequestMapping("/api/v1/admin/shareholding-pattern")
@PreAuthorize("hasRole('ADMIN')")
public class ShareholdingAdminController {

    private final OwnershipInstrumentLookup instrumentLookup;
    private final ShareholdingLoader loader;

    public ShareholdingAdminController(OwnershipInstrumentLookup instrumentLookup, ShareholdingLoader loader) {
        this.instrumentLookup = instrumentLookup;
        this.loader = loader;
    }

    @Operation(summary = "Record one quarter's shareholding pattern for a tracked instrument", description = "Upserts on (instrument, period) - safe to re-submit to correct a figure.")
    @PostMapping
    public ResponseEntity<ShareholdingPattern> create(@Valid @RequestBody AddShareholdingRequest request) {
        UUID instrumentId = instrumentLookup.findIdBySymbol(request.symbol())
            .orElseThrow(() -> new IllegalArgumentException(request.symbol() + " isn't a tracked instrument"));

        ShareholdingPattern pattern = new ShareholdingPattern(
            instrumentId, request.symbol(), request.periodEnd(),
            request.promoterPercentage(), request.fiiPercentage(), request.diiPercentage(),
            request.mfPercentage(), request.publicPercentage()
        );
        loader.load(pattern);

        return ResponseEntity.status(HttpStatus.CREATED).body(pattern);
    }
}
