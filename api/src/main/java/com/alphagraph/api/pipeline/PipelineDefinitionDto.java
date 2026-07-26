package com.alphagraph.api.pipeline;

import java.util.UUID;

public record PipelineDefinitionDto(UUID id, String name, String module, String cronExpression, boolean active) {
}
