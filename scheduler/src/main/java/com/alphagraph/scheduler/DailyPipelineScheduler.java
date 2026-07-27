package com.alphagraph.scheduler;

import com.alphagraph.common.etl.Collector;
import com.alphagraph.common.etl.Loader;
import com.alphagraph.common.etl.Normalizer;
import com.alphagraph.common.etl.Parser;
import com.alphagraph.common.etl.PipelineDefinition;
import com.alphagraph.common.etl.RequiredFieldsValidator;
import com.alphagraph.common.etl.SourceConfig;
import com.alphagraph.common.etl.Validator;
import com.alphagraph.common.quality.DataQualitySpec;
import com.alphagraph.scheduler.orchestration.PipelineOrchestrator;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The 6 PM trigger from docs/002_Engine_Architecture.md §6. No manual execution: every pipeline
 * run happens through here (the cron) or through {@link #runDummyPipeline()} directly (the
 * "authenticated admin API call that re-runs a named pipeline" the doc describes — Module 0.9
 * exposes that as a REST endpoint, this method is what it will call).
 *
 * Phase 1 has no real domain source yet, so this registers one dummy pipeline to prove the
 * whole flow - Download, Validate, Process, Data Quality Gate, Calculate, Score, Notify -
 * actually runs end to end, per the Phase 0 deliverable ("imports dummy data, stores, REST API
 * returns data").
 */
@Component
public class DailyPipelineScheduler {

    private static final String CRON_6PM_IST = "0 0 18 * * *";

    private record DummyRecord(String symbol, String value) {
    }

    private final PipelineOrchestrator orchestrator;

    public DailyPipelineScheduler(PipelineOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Scheduled(cron = CRON_6PM_IST, zone = "Asia/Kolkata")
    public void runScheduledPipelines() {
        // @Scheduled runs on a scheduler thread with no HTTP request behind it, so there's no
        // X-Request-Id for api's CorrelationIdFilter to have set - generate one here so this
        // run's log lines and pipeline_executions row are still traceable as one unit.
        MDC.put("requestId", "cron-" + UUID.randomUUID());
        try {
            runDummyPipeline();
        } finally {
            MDC.remove("requestId");
        }
    }

    public void runDummyPipeline() {
        SourceConfig sourceConfig = new SourceConfig("phase0-dummy-source", "scheduler");

        Collector<List<String[]>> collector = config -> List.<String[]>of(new String[] {"DUMMY", "1"});

        Parser<List<String[]>, DummyRecord> parser = raw -> raw.stream()
            .map(row -> new DummyRecord(row[0], row[1]))
            .toList();

        Validator<DummyRecord> validator = new RequiredFieldsValidator<>(Map.of(
            "symbol", DummyRecord::symbol,
            "value", DummyRecord::value
        ));

        Normalizer<DummyRecord, DummyRecord> normalizer = record -> record;

        Loader<DummyRecord> loader = record -> { };

        PipelineDefinition<List<String[]>, DummyRecord, DummyRecord> definition = new PipelineDefinition<>(
            sourceConfig, collector, parser, validator, normalizer, loader
        );

        DataQualitySpec<DummyRecord> qualitySpec = new DataQualitySpec<>(
            Map.of("symbol", DummyRecord::symbol, "value", DummyRecord::value),
            Set.of("symbol", "value"),
            DummyRecord::symbol
        );

        orchestrator.run(definition, qualitySpec, CRON_6PM_IST);
    }
}
