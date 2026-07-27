package com.alphagraph.scheduler.persistence;

import com.alphagraph.common.etl.PipelineRunResult;
import com.alphagraph.common.quality.DataQualityScore;

import java.util.UUID;

/**
 * Persists what Module 0.10 logging requires (scheduler.pipeline_executions /
 * pipeline_execution_errors) plus the batch's data quality score (common.data_quality_scores).
 * Kept as an interface so {@link com.alphagraph.scheduler.orchestration.PipelineOrchestrator}
 * can be unit tested without a real database.
 */
public interface PipelineExecutionRecorder {

    UUID ensurePipelineDefinition(String name, String module, String cronExpression);

    UUID startExecution(UUID pipelineId, String correlationId);

    void completeExecution(UUID executionId, PipelineRunResult result);

    void recordDataQualityScore(UUID executionId, DataQualityScore score);
}
