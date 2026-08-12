package com.alphagraph.api.admin;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin observability: execution status/timestamp/summary for every cron in the cascade
 * (docs/001_System_Architecture.md cron sequence), and live reachability of every external source
 * the collectors depend on. Read-only - this reports what happened and what's reachable right
 * now, it doesn't let an admin re-trigger a run (no such endpoint exists yet for the 15 standalone
 * schedulers, unlike POST /pipeline-definitions/{id}/run for the 9 ETL pipelines).
 */
@RestController
@RequestMapping("/api/v1/admin/monitoring")
@PreAuthorize("hasRole('ADMIN')")
public class MonitoringController {

    private final CronMonitoringRepository cronMonitoringRepository;
    private final LiveSourceHealthService liveSourceHealthService;

    public MonitoringController(CronMonitoringRepository cronMonitoringRepository, LiveSourceHealthService liveSourceHealthService) {
        this.cronMonitoringRepository = cronMonitoringRepository;
        this.liveSourceHealthService = liveSourceHealthService;
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
}
