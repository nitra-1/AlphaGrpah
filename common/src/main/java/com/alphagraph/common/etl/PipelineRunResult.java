package com.alphagraph.common.etl;

import java.time.Instant;
import java.util.List;

/**
 * Summary of one {@link Pipeline#run()} call. Field names deliberately mirror
 * {@code scheduler.pipeline_executions} (docs/003_Database_Architecture.md §3) — the scheduler
 * module persists this into that table, Pipeline itself never touches the database.
 */
public record PipelineRunResult(
    String pipelineName,
    Instant startedAt,
    Instant finishedAt,
    PipelineStatus status,
    int rowsRead,
    int rowsAccepted,
    int rowsRejected,
    List<String> errors
) {

    public PipelineRunResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}
