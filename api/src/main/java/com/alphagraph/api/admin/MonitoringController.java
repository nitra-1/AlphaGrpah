package com.alphagraph.api.admin;

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

/**
 * Admin observability for every cron in the cascade (docs/001_System_Architecture.md cron
 * sequence): execution status/timestamp/summary, live reachability of every external source the
 * collectors depend on, and - via {@link #retry} - manual re-trigger by name for a cron that
 * missed its scheduled run today ({@link CronStatusDto#missedToday}). Standalone jobs are
 * dispatched through {@link JobRegistry}; ETL pipelines reuse the same
 * {@link PipelineRegistry}/{@link PipelineOrchestrator} pair {@code PipelineDefinitionController}
 * already triggers by id - this endpoint just looks the same pipeline up by name instead, so the
 * frontend has one retry call that works for either row type without branching on {@code source}.
 */
@RestController
@RequestMapping("/api/v1/admin/monitoring")
@PreAuthorize("hasRole('ADMIN')")
public class MonitoringController {

    private final CronMonitoringRepository cronMonitoringRepository;
    private final LiveSourceHealthService liveSourceHealthService;
    private final JobRegistry jobRegistry;
    private final PipelineRegistry pipelineRegistry;
    private final PipelineOrchestrator pipelineOrchestrator;

    public MonitoringController(
        CronMonitoringRepository cronMonitoringRepository, LiveSourceHealthService liveSourceHealthService,
        JobRegistry jobRegistry, PipelineRegistry pipelineRegistry, PipelineOrchestrator pipelineOrchestrator
    ) {
        this.cronMonitoringRepository = cronMonitoringRepository;
        this.liveSourceHealthService = liveSourceHealthService;
        this.jobRegistry = jobRegistry;
        this.pipelineRegistry = pipelineRegistry;
        this.pipelineOrchestrator = pipelineOrchestrator;
    }

    @Operation(summary = "Execution status, last-run timestamps, and a short activity summary for every cron in the pipeline")
    @GetMapping("/crons")
    public List<CronStatusDto> crons() {
        return cronMonitoringRepository.findAll();
    }

    @Operation(summary = "Live connectivity check, run right now, against every external source the collectors depend on")
    @GetMapping("/sources")
    public List<LiveSourceStatusDto> sources() {
        return liveSourceHealthService.checkAll();
    }

    @Operation(
        summary = "Manually re-trigger a cron by name",
        description = "Runs synchronously, right now - same as the real scheduled firing (standalone jobs) or the existing pipeline admin-run endpoint (ETL pipelines)."
    )
    @PostMapping("/crons/{name}/retry")
    public CronRetryResponse retry(@PathVariable String name) {
        if (jobRegistry.contains(name)) {
            jobRegistry.trigger(name);
            return new CronRetryResponse(name, "Run completed");
        }
        ScheduledPipeline pipeline = pipelineRegistry.findByName(name)
            .orElseThrow(() -> new NotFoundException("No cron with name " + name));
        pipeline.run(pipelineOrchestrator);
        return new CronRetryResponse(name, "Run completed");
    }
}
