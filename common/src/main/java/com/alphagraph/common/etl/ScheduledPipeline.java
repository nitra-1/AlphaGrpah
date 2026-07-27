package com.alphagraph.common.etl;

/**
 * A pipeline a domain module registers for the scheduler to discover and run — per
 * docs/001_System_Architecture.md §4 ("scheduler... orchestrates concrete pipelines registered
 * by domain modules"). Each implementation builds its own concrete {@link PipelineDefinition}
 * and {@link com.alphagraph.common.quality.DataQualitySpec} internally and hands them to the
 * {@link PipelineRunner} it's given — this is what lets the scheduler hold a plain
 * {@code List<ScheduledPipeline>} without needing to know any pipeline's concrete generic types.
 */
public interface ScheduledPipeline {

    String name();

    void run(PipelineRunner runner);
}
