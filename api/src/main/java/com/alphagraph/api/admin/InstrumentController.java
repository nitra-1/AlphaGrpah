package com.alphagraph.api.admin;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/instruments")
@PreAuthorize("hasRole('ADMIN')")
public class InstrumentController {

    private final InstrumentAdditionService additionService;

    public InstrumentController(InstrumentAdditionService additionService) {
        this.additionService = additionService;
    }

    @Operation(
        summary = "Track a new instrument",
        description = "Historical price backfill starts in the background immediately after and isn't complete when this returns."
    )
    @PostMapping
    public ResponseEntity<InstrumentDto> create(@Valid @RequestBody CreateInstrumentRequest request) {
        InstrumentDto created = additionService.addInstrument(request.symbol(), request.sectorName());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
