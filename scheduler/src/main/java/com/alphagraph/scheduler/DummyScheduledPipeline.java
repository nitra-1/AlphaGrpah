package com.alphagraph.scheduler;

import com.alphagraph.common.etl.Collector;
import com.alphagraph.common.etl.Loader;
import com.alphagraph.common.etl.Normalizer;
import com.alphagraph.common.etl.Parser;
import com.alphagraph.common.etl.PipelineDefinition;
import com.alphagraph.common.etl.PipelineRunner;
import com.alphagraph.common.etl.RequiredFieldsValidator;
import com.alphagraph.common.etl.ScheduledPipeline;
import com.alphagraph.common.etl.SourceConfig;
import com.alphagraph.common.etl.Validator;
import com.alphagraph.common.quality.DataQualitySpec;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phase 0's proof pipeline — no real domain source existed yet then. Kept alongside real
 * pipelines (Module 1.1's market data) as a cheap end-to-end smoke test of the Download ->
 * Validate -> Process -> Data Quality Gate -> Calculate -> Score -> Notify flow that doesn't
 * depend on any domain module's data being available.
 */
@Component
public class DummyScheduledPipeline implements ScheduledPipeline {

    private static final String CRON_6PM_IST = "0 0 18 * * *";

    private record DummyRecord(String symbol, String value) {
    }

    @Override
    public String name() {
        return "phase0-dummy-source";
    }

    @Override
    public void run(PipelineRunner runner) {
        SourceConfig sourceConfig = new SourceConfig(name(), "scheduler");

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

        runner.run(definition, qualitySpec, CRON_6PM_IST);
    }
}
