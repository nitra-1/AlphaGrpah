package com.alphagraph.common.etl;

/**
 * Mirrors the {@code status} CHECK constraint on {@code scheduler.pipeline_executions}
 * (docs/003_Database_Architecture.md §3) — keep the two in sync if either changes.
 */
public enum PipelineStatus {
    RUNNING,
    SUCCESS,
    FAILED,
    PARTIAL
}
