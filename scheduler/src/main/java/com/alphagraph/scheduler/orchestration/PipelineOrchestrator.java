package com.alphagraph.scheduler.orchestration;

import com.alphagraph.common.engine.NullEngine;
import com.alphagraph.common.engine.SimpleScore;
import com.alphagraph.common.etl.Pipeline;
import com.alphagraph.common.etl.PipelineDefinition;
import com.alphagraph.common.etl.PipelineOutcome;
import com.alphagraph.common.quality.DataQualityEngine;
import com.alphagraph.common.quality.DataQualityInput;
import com.alphagraph.common.quality.DataQualityScore;
import com.alphagraph.common.quality.DataQualitySpec;
import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.scheduler.notification.NotificationPort;
import com.alphagraph.scheduler.persistence.PipelineExecutionRecorder;

import java.util.List;
import java.util.UUID;

/**
 * Drives the full flow from docs/002_Engine_Architecture.md §6: Download -> Validate -> Process
 * (all three are {@link Pipeline#run()}) -> Data Quality Gate -> Calculate -> Score -> Notify.
 * This is the only place that constructs and runs a {@link Pipeline} — domain modules register
 * {@link PipelineDefinition}s, they never drive them, per docs/001_System_Architecture.md §4.
 */
public final class PipelineOrchestrator {

    private final PipelineExecutionRecorder recorder;
    private final DataQualityEngine dataQualityEngine;
    private final NotificationPort notificationPort;
    private final double qualityFloor;

    public PipelineOrchestrator(
        PipelineExecutionRecorder recorder, DataQualityEngine dataQualityEngine,
        NotificationPort notificationPort, double qualityFloor
    ) {
        this.recorder = recorder;
        this.dataQualityEngine = dataQualityEngine;
        this.notificationPort = notificationPort;
        this.qualityFloor = qualityFloor;
    }

    public <R, T, D> void run(PipelineDefinition<R, T, D> definition, DataQualitySpec<T> qualitySpec, String cronExpression) {
        String name = definition.sourceConfig().name();
        String module = definition.sourceConfig().module();

        UUID pipelineId = recorder.ensurePipelineDefinition(name, module, cronExpression);
        UUID executionId = recorder.startExecution(pipelineId);

        PipelineOutcome<T> outcome = new Pipeline<>(definition).run();
        recorder.completeExecution(executionId, outcome.result());

        DataQualityInput input = DataQualityInput.from(outcome.parsedRecords(), qualitySpec, outcome.result().rowsRejected());
        DataQualityScore qualityScore = dataQualityEngine.score(input);
        recorder.recordDataQualityScore(executionId, qualityScore);

        if (qualityScore.score() < qualityFloor) {
            notificationPort.notify(name, "Quarantined - data quality score %.2f is below the floor of %.2f"
                .formatted(qualityScore.score(), qualityFloor));
            return;
        }

        // Phase 0 has no real Calculate step yet (Phase 1 engines don't exist) - NullEngine
        // proves this stage of the flow runs, nothing more. See common.engine.NullEngine.
        SimpleScore score = new NullEngine().calculate(null, new RuleSet(0, List.of()));

        notificationPort.notify(name, "Completed - status=%s, rowsAccepted=%d, rowsRejected=%d, score=%.2f"
            .formatted(outcome.result().status(), outcome.result().rowsAccepted(), outcome.result().rowsRejected(), score.value()));
    }
}
