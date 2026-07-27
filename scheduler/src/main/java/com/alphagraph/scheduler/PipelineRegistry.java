package com.alphagraph.scheduler;

import com.alphagraph.common.etl.ScheduledPipeline;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Every {@link ScheduledPipeline} bean any module has registered, auto-collected by Spring.
 * This is the "real dispatch registry" flagged as a TODO in Module 0.9's
 * PipelineDefinitionController, now that Module 1.1 registers a second pipeline.
 */
@Component
public class PipelineRegistry {

    private final List<ScheduledPipeline> pipelines;

    public PipelineRegistry(List<ScheduledPipeline> pipelines) {
        this.pipelines = List.copyOf(pipelines);
    }

    public List<ScheduledPipeline> all() {
        return pipelines;
    }

    public Optional<ScheduledPipeline> findByName(String name) {
        return pipelines.stream().filter(pipeline -> pipeline.name().equals(name)).findFirst();
    }
}
