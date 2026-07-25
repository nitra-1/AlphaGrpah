package com.alphagraph.scheduler.orchestration;

import com.alphagraph.common.etl.Collector;
import com.alphagraph.common.etl.Loader;
import com.alphagraph.common.etl.Normalizer;
import com.alphagraph.common.etl.Parser;
import com.alphagraph.common.etl.PipelineDefinition;
import com.alphagraph.common.etl.PipelineRunResult;
import com.alphagraph.common.etl.RequiredFieldsValidator;
import com.alphagraph.common.etl.SourceConfig;
import com.alphagraph.common.etl.Validator;
import com.alphagraph.common.quality.DataQualityEngine;
import com.alphagraph.common.quality.DataQualityScore;
import com.alphagraph.common.quality.DataQualitySpec;
import com.alphagraph.scheduler.notification.NotificationPort;
import com.alphagraph.scheduler.persistence.PipelineExecutionRecorder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineOrchestratorTest {

    private record Row(String symbol, String value) {
    }

    private static class FakeRecorder implements PipelineExecutionRecorder {
        UUID pipelineId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        PipelineRunResult completedWith;
        DataQualityScore recordedScore;
        final Map<String, String> ensuredDefinitions = new HashMap<>();

        @Override
        public UUID ensurePipelineDefinition(String name, String module, String cronExpression) {
            ensuredDefinitions.put(name, module);
            return pipelineId;
        }

        @Override
        public UUID startExecution(UUID pid) {
            assertThat(pid).isEqualTo(pipelineId);
            return executionId;
        }

        @Override
        public void completeExecution(UUID executionId, PipelineRunResult result) {
            assertThat(executionId).isEqualTo(this.executionId);
            this.completedWith = result;
        }

        @Override
        public void recordDataQualityScore(UUID executionId, DataQualityScore score) {
            assertThat(executionId).isEqualTo(this.executionId);
            this.recordedScore = score;
        }
    }

    private static class FakeNotificationPort implements NotificationPort {
        final List<String> messages = new ArrayList<>();

        @Override
        public void notify(String subject, String message) {
            messages.add(subject + ": " + message);
        }
    }

    private static PipelineDefinition<List<String[]>, Row, Row> definitionFor(List<String[]> rawRows) {
        SourceConfig sourceConfig = new SourceConfig("test-source", "scheduler");
        Collector<List<String[]>> collector = config -> rawRows;
        Parser<List<String[]>, Row> parser = raw -> raw.stream()
            .map(r -> new Row(r[0], r.length > 1 ? r[1] : null))
            .toList();
        Validator<Row> validator = new RequiredFieldsValidator<>(Map.of("symbol", Row::symbol, "value", Row::value));
        Normalizer<Row, Row> normalizer = r -> r;
        Loader<Row> loader = r -> { };
        return new PipelineDefinition<>(sourceConfig, collector, parser, validator, normalizer, loader);
    }

    private static DataQualitySpec<Row> qualitySpec() {
        return new DataQualitySpec<>(
            Map.of("symbol", Row::symbol, "value", Row::value),
            Set.of("symbol", "value"),
            Row::symbol
        );
    }

    @Test
    void goodBatchPassesTheQualityGateAndNotifiesCompletion() {
        FakeRecorder recorder = new FakeRecorder();
        FakeNotificationPort notifications = new FakeNotificationPort();
        PipelineOrchestrator orchestrator = new PipelineOrchestrator(recorder, new DataQualityEngine(), notifications, 0.7);

        List<String[]> rawRows = List.of(new String[] {"NSE:BEML", "1500.5"}, new String[] {"NSE:ACE", "620.0"});
        orchestrator.run(definitionFor(rawRows), qualitySpec(), "0 0 18 * * *");

        assertThat(recorder.ensuredDefinitions).containsEntry("test-source", "scheduler");
        assertThat(recorder.completedWith.status().name()).isEqualTo("SUCCESS");
        assertThat(recorder.recordedScore.score()).isEqualTo(1.0);
        assertThat(notifications.messages).hasSize(1);
        assertThat(notifications.messages.get(0)).startsWith("test-source: Completed");
    }

    @Test
    void badBatchIsQuarantinedAndNeverNotifiedAsCompleted() {
        FakeRecorder recorder = new FakeRecorder();
        FakeNotificationPort notifications = new FakeNotificationPort();
        PipelineOrchestrator orchestrator = new PipelineOrchestrator(recorder, new DataQualityEngine(), notifications, 0.7);

        // Every row missing "value" -> validation rejects all of them -> quality score well below 0.7.
        List<String[]> rawRows = List.of(new String[] {"NSE:BEML"}, new String[] {"NSE:ACE"});
        orchestrator.run(definitionFor(rawRows), qualitySpec(), "0 0 18 * * *");

        assertThat(recorder.recordedScore).isNotNull();
        assertThat(recorder.recordedScore.score()).isLessThan(0.7);
        assertThat(notifications.messages).hasSize(1);
        assertThat(notifications.messages.get(0)).startsWith("test-source: Quarantined");
    }
}
