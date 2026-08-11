package com.alphagraph.api.admin;

import com.alphagraph.api.error.NotFoundException;
import com.alphagraph.reference.instrument.InstrumentReader;
import com.alphagraph.reference.instrument.InstrumentWriter;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/instruments")
@PreAuthorize("hasRole('ADMIN')")
public class InstrumentController {

    private final InstrumentAdditionService additionService;
    private final InstrumentReader instrumentReader;
    private final InstrumentWriter instrumentWriter;

    public InstrumentController(InstrumentAdditionService additionService, InstrumentReader instrumentReader, InstrumentWriter instrumentWriter) {
        this.additionService = additionService;
        this.instrumentReader = instrumentReader;
        this.instrumentWriter = instrumentWriter;
    }

    @Operation(summary = "List currently tracked instruments", description = "Powers the financial/shareholding data-entry form's instrument picker, and the Sectors page's reassignment table. Includes each instrument's current sector.")
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

    @Operation(
        summary = "Reassign an instrument's sector",
        description = "sectorId may be null to clear it. 400s if sectorId is given but doesn't exist - lets an instrument blocking a sector's deletion actually be moved, not just diagnosed."
    )
    @PutMapping("/{id}/sector")
    public ResponseEntity<Void> reassignSector(@PathVariable UUID id, @RequestBody ReassignSectorRequest request) {
        if (!instrumentWriter.updateSector(id, request.sectorId())) {
            throw new NotFoundException("No instrument with id " + id);
        }
        return ResponseEntity.noContent().build();
    }
}
