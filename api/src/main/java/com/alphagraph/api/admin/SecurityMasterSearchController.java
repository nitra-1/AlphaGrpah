package com.alphagraph.api.admin;

import com.alphagraph.reference.securitymaster.SecurityMasterReader;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Powers the (future) "Add Instrument" admin form's autocomplete - a non-technical admin picks a
 * real symbol+ISIN from NSE's actual listed universe instead of typing either by hand, per
 * docs/006_Universe_Expansion_Runbook.md. Search-only; nothing here writes to
 * reference.instruments - adding a real tracked instrument is a separate, deliberate step.
 */
@RestController
@RequestMapping("/api/v1/admin/security-master")
@PreAuthorize("hasRole('ADMIN')")
public class SecurityMasterSearchController {

    private final SecurityMasterReader reader;

    public SecurityMasterSearchController(SecurityMasterReader reader) {
        this.reader = reader;
    }

    @Operation(summary = "Search NSE's full listed-equity universe by symbol or company name")
    @GetMapping("/search")
    public List<SecurityMasterEntryDto> search(@RequestParam String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return reader.search(query).stream().map(SecurityMasterEntryDto::from).toList();
    }
}
