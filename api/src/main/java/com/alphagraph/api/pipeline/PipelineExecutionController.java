package com.alphagraph.api.pipeline;

import com.alphagraph.api.PageResponse;
import com.alphagraph.api.error.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pipeline-executions")
public class PipelineExecutionController {

    private final PipelineReadRepository repository;

    public PipelineExecutionController(PipelineReadRepository repository) {
        this.repository = repository;
    }

    @Operation(summary = "List pipeline runs")
    @GetMapping
    public PageResponse<PipelineExecutionSummaryDto> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        return repository.findExecutions(page, size);
    }

    @Operation(summary = "Run detail including errors and data quality score")
    @GetMapping("/{id}")
    public PipelineExecutionDetailDto get(@PathVariable UUID id) {
        return repository.findExecutionDetail(id)
            .orElseThrow(() -> new NotFoundException("No pipeline execution with id " + id));
    }
}
