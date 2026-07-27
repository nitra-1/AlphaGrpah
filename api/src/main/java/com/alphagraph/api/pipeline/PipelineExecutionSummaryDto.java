package com.alphagraph.api.pipeline;

import java.time.Instant;
import java.util.UUID;

public record PipelineExecutionSummaryDto(
    UUID id, String pipelineName, String status, Instant startedAt, Instant finishedAt,
    int rowsRead, int rowsAccepted, int rowsRejected, String correlationId
) {
}
