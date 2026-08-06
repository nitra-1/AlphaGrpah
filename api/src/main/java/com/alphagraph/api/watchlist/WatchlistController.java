package com.alphagraph.api.watchlist;

import com.alphagraph.api.error.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Module 3.2: the single global watchlist. GET needs no role beyond a valid JWT, matching every other read endpoint; mutations require ADMIN, matching api.rule.RuleController's convention. */
@RestController
@RequestMapping("/api/v1/watchlist")
public class WatchlistController {

    private final WatchlistViewService viewService;

    public WatchlistController(WatchlistViewService viewService) {
        this.viewService = viewService;
    }

    @Operation(summary = "List the watchlist", description = "Every watched instrument with its current Swing/Long-Term Score and Rank, if computed yet.")
    @GetMapping
    public List<WatchlistEntryDto> list() {
        return viewService.list();
    }

    @Operation(summary = "Add an instrument to the watchlist", description = "No-op if already on the list.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<WatchlistEntryDto> add(@Valid @RequestBody AddWatchlistItemRequest request) {
        WatchlistEntryDto entry = viewService.add(request.instrumentId())
            .orElseThrow(() -> new NotFoundException("No instrument with id " + request.instrumentId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(entry);
    }

    @Operation(summary = "Remove an instrument from the watchlist")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{instrumentId}")
    public ResponseEntity<Void> remove(@PathVariable UUID instrumentId) {
        if (!viewService.remove(instrumentId)) {
            throw new NotFoundException("Instrument " + instrumentId + " is not on the watchlist");
        }
        return ResponseEntity.noContent().build();
    }
}
