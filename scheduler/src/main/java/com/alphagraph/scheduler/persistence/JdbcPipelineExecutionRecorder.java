package com.alphagraph.scheduler.persistence;

import com.alphagraph.common.etl.PipelineRunResult;
import com.alphagraph.common.quality.DataQualityScore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Writes to scheduler.pipeline_definitions/pipeline_executions/pipeline_execution_errors and common.data_quality_scores. */
@Component
public class JdbcPipelineExecutionRecorder implements PipelineExecutionRecorder {

    private final JdbcTemplate jdbcTemplate;

    public JdbcPipelineExecutionRecorder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UUID ensurePipelineDefinition(String name, String module, String cronExpression) {
        List<UUID> existing = jdbcTemplate.query(
            "SELECT id FROM scheduler.pipeline_definitions WHERE name = ?",
            (rs, rowNum) -> (UUID) rs.getObject("id"),
            name
        );
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO scheduler.pipeline_definitions (id, name, module, cron_expression, active) VALUES (?, ?, ?, ?, true)",
            id, name, module, cronExpression
        );
        return id;
    }

    @Override
    public UUID startExecution(UUID pipelineId, String correlationId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO scheduler.pipeline_executions (id, pipeline_id, status, correlation_id) VALUES (?, ?, 'RUNNING', ?)",
            id, pipelineId, correlationId
        );
        return id;
    }

    @Override
    public void completeExecution(UUID executionId, PipelineRunResult result) {
        jdbcTemplate.update(
            """
            UPDATE scheduler.pipeline_executions
            SET finished_at = now(), status = ?, rows_read = ?, rows_accepted = ?, rows_rejected = ?
            WHERE id = ?
            """,
            result.status().name(), result.rowsRead(), result.rowsAccepted(), result.rowsRejected(), executionId
        );

        for (String error : result.errors()) {
            jdbcTemplate.update(
                "INSERT INTO scheduler.pipeline_execution_errors (id, pipeline_execution_id, message) VALUES (?, ?, ?)",
                UUID.randomUUID(), executionId, error
            );
        }
    }

    @Override
    public void recordDataQualityScore(UUID executionId, DataQualityScore score) {
        jdbcTemplate.update(
            """
            INSERT INTO common.data_quality_scores
                (id, pipeline_execution_id, completeness, duplicate_rate, missing_field_rate, validation_error_rate, score)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(), executionId, score.completeness(), score.duplicateRate(),
            score.missingFieldRate(), score.validationErrorRate(), score.score()
        );
    }
}
