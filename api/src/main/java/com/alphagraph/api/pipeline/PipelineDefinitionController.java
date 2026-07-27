package com.alphagraph.api.pipeline;

import com.alphagraph.api.error.NotFoundException;
import com.alphagraph.common.etl.ScheduledPipeline;
import com.alphagraph.scheduler.PipelineRegistry;
import com.alphagraph.scheduler.orchestration.PipelineOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pipeline-definitions")
public class PipelineDefinitionController {

    private final PipelineReadRepository repository;
    private final PipelineRegistry registry;
    private final PipelineOrchestrator orchestrator;

    public PipelineDefinitionController(PipelineReadRepository repository, PipelineRegistry registry, PipelineOrchestrator orchestrator) {
        this.repository = repository;
        this.registry = registry;
        this.orchestrator = orchestrator;
    }

    @Operation(summary = "List registered pipelines")
    @GetMapping
    public List<PipelineDefinitionDto> list() {
        return repository.findAllDefinitions();
    }

    @Operation(
        summary = "Manually trigger a pipeline",
        description = "Admin recovery path - the only sanctioned manual execution besides the 6PM cron, per docs/002_Engine_Architecture.md §6."
    )
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/run")
    public RunTriggeredResponse run(@PathVariable UUID id) {
        PipelineDefinitionDto definition = repository.findDefinitionById(id)
            .orElseThrow(() -> new NotFoundException("No pipeline definition with id " + id));

        ScheduledPipeline pipeline = registry.findByName(definition.name())
            .orElseThrow(() -> new NotFoundException("No runnable handler registered for pipeline " + definition.name()));

        pipeline.run(orchestrator);
        return new RunTriggeredResponse(definition.name(), "Run completed");
    }
}
