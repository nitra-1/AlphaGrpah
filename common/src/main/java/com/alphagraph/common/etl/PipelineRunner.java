package com.alphagraph.common.etl;

import com.alphagraph.common.quality.DataQualitySpec;

/**
 * The capability {@link com.alphagraph.scheduler.orchestration.PipelineOrchestrator} (in the
 * scheduler module) provides — declared here instead so a domain module's {@link
 * ScheduledPipeline} can depend on it without common depending on scheduler (which would be
 * circular; scheduler already depends on common).
 */
public interface PipelineRunner {

    <R, T, D> void run(PipelineDefinition<R, T, D> definition, DataQualitySpec<T> qualitySpec, String cronExpression);
}
