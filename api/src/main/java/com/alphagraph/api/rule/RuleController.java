package com.alphagraph.api.rule;

import com.alphagraph.api.error.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rule-definitions")
public class RuleController {

    private final RuleRepository repository;

    public RuleController(RuleRepository repository) {
        this.repository = repository;
    }

    @Operation(summary = "List rules")
    @GetMapping
    public List<RuleDefinitionDto> list() {
        return repository.findAll();
    }

    @Operation(summary = "Create a new rule version", description = "Starts inactive - call activate to make it live.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<RuleDefinitionDto> create(@Valid @RequestBody CreateRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.create(request));
    }

    @Operation(summary = "Activate a rule version", description = "Deactivates the prior active version of the same name.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/activate")
    public RuleDefinitionDto activate(@PathVariable UUID id) {
        return repository.activate(id)
            .orElseThrow(() -> new NotFoundException("No rule definition with id " + id));
    }
}
