package com.alphagraph.api.admin;

import com.alphagraph.api.error.NotFoundException;
import com.alphagraph.reference.instrument.SectorService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Full sector CRUD for the admin Sectors page - also powers the Add Instrument form's dropdown, which only reads {@code id}/{@code name}. */
@RestController
@RequestMapping("/api/v1/admin/sectors")
@PreAuthorize("hasRole('ADMIN')")
public class SectorController {

    private final SectorService sectorService;

    public SectorController(SectorService sectorService) {
        this.sectorService = sectorService;
    }

    @Operation(summary = "List every sector", description = "Includes each sector's parent name and current instrument count.")
    @GetMapping
    public List<SectorDto> list() {
        return sectorService.listAllWithDetail().stream().map(SectorDto::from).toList();
    }

    @Operation(summary = "Create a sector", description = "400s if the name is already taken (case-insensitive) or parentSectorId doesn't exist.")
    @PostMapping
    public ResponseEntity<UUID> create(@Valid @RequestBody SectorRequest request) {
        UUID id = sectorService.create(request.name(), request.parentSectorId());
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    @Operation(
        summary = "Rename or re-parent a sector",
        description = "400s if the new name is already taken by a different sector, the sector would become its own parent, or the new parent is one of its own descendants."
    )
    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable UUID id, @Valid @RequestBody SectorRequest request) {
        if (!sectorService.update(id, request.name(), request.parentSectorId())) {
            throw new NotFoundException("No sector with id " + id);
        }
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Delete a sector",
        description = "400s if any instrument is still assigned to it or any sub-sector still exists under it - reassign or delete those first."
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        if (!sectorService.delete(id)) {
            throw new NotFoundException("No sector with id " + id);
        }
        return ResponseEntity.noContent().build();
    }
}
