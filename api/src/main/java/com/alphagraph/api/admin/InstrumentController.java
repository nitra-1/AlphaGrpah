package com.alphagraph.api.admin;

import com.alphagraph.reference.instrument.InstrumentReader;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/instruments")
@PreAuthorize("hasRole('ADMIN')")
public class InstrumentController {

    private final InstrumentAdditionService additionService;
    private final InstrumentReader instrumentReader;

    public InstrumentController(InstrumentAdditionService additionService, InstrumentReader instrumentReader) {
        this.additionService = additionService;
        this.instrumentReader = instrumentReader;
    }

    @Operation(summary = "List currently tracked instruments", description = "Powers the financial/shareholding data-entry form's instrument picker.")
    @GetMapping
    public List<TrackedInstrumentDto> list() {
        return instrumentReader.listAll().stream().map(TrackedInstrumentDto::from).toList();
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
