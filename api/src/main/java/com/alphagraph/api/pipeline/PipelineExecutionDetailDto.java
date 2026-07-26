package com.alphagraph.api.pipeline;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PipelineExecutionDetailDto(
    UUID id, String pipelineName, String status, Instant startedAt, Instant finishedAt,
    int rowsRead, int rowsAccepted, int rowsRejected, int retryCount,
    List<String> errors, DataQualityScoreDto dataQualityScore
) {
}
