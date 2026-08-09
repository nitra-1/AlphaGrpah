package com.alphagraph.api.admin;

import com.alphagraph.reference.instrument.SectorService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/sectors")
@PreAuthorize("hasRole('ADMIN')")
public class SectorController {

    private final SectorService sectorService;

    public SectorController(SectorService sectorService) {
        this.sectorService = sectorService;
    }

    @Operation(summary = "List existing sectors for the Add Instrument form's dropdown")
    @GetMapping
    public List<SectorDto> list() {
        return sectorService.listAll().stream().map(SectorDto::from).toList();
    }
}
