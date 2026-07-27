package com.alphagraph.scheduler;

import com.alphagraph.common.etl.ScheduledPipeline;
import com.alphagraph.scheduler.orchestration.PipelineOrchestrator;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The 6 PM trigger from docs/002_Engine_Architecture.md §6. No manual execution: every pipeline
 * run happens through here (the cron, for every {@link ScheduledPipeline} in the
 * {@link PipelineRegistry}) or through the admin recovery endpoint
 * (api's PipelineDefinitionController, which looks a pipeline up in the same registry by name).
 */
@Component
public class DailyPipelineScheduler {

    private static final String CRON_6PM_IST = "0 0 18 * * *";

    private final PipelineOrchestrator orchestrator;
    private final PipelineRegistry registry;

    public DailyPipelineScheduler(PipelineOrchestrator orchestrator, PipelineRegistry registry) {
        this.orchestrator = orchestrator;
        this.registry = registry;
    }

    @Scheduled(cron = CRON_6PM_IST, zone = "Asia/Kolkata")
    public void runScheduledPipelines() {
        for (ScheduledPipeline pipeline : registry.all()) {
            // @Scheduled runs on a scheduler thread with no HTTP request behind it, so there's
            // no X-Request-Id for api's CorrelationIdFilter to have set. Fresh id per pipeline
            // (not one for the whole tick) since each run is a logically distinct unit of work.
            MDC.put("requestId", "cron-" + UUID.randomUUID());
            try {
                pipeline.run(orchestrator);
            } finally {
                MDC.remove("requestId");
            }
        }
    }
}
