package com.alphagraph.api.pipeline;

import com.alphagraph.api.PageResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only access to scheduler.pipeline_definitions/pipeline_executions/
 * pipeline_execution_errors and common.data_quality_scores for the endpoints in
 * docs/004_API_Architecture.md §6. Deliberately separate from
 * scheduler.persistence.PipelineExecutionRecorder, which is write-only and shaped for
 * orchestration, not for listing/paginating.
 */
@Repository
public class PipelineReadRepository {

    private static final RowMapper<PipelineDefinitionDto> DEFINITION_MAPPER = (rs, rowNum) -> new PipelineDefinitionDto(
        (UUID) rs.getObject("id"), rs.getString("name"), rs.getString("module"),
        rs.getString("cron_expression"), rs.getBoolean("active")
    );

    private final JdbcTemplate jdbcTemplate;

    public PipelineReadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PipelineDefinitionDto> findAllDefinitions() {
        return jdbcTemplate.query(
            "SELECT id, name, module, cron_expression, active FROM scheduler.pipeline_definitions ORDER BY name",
            DEFINITION_MAPPER
        );
    }

    public Optional<PipelineDefinitionDto> findDefinitionById(UUID id) {
        return jdbcTemplate.query(
            "SELECT id, name, module, cron_expression, active FROM scheduler.pipeline_definitions WHERE id = ?",
            DEFINITION_MAPPER, id
        ).stream().findFirst();
    }

    public PageResponse<PipelineExecutionSummaryDto> findExecutions(int page, int size) {
        long totalElements = jdbcTemplate.queryForObject("SELECT count(*) FROM scheduler.pipeline_executions", Long.class);

        List<PipelineExecutionSummaryDto> content = jdbcTemplate.query(
            """
            SELECT e.id, d.name AS pipeline_name, e.status, e.started_at, e.finished_at,
                   e.rows_read, e.rows_accepted, e.rows_rejected
            FROM scheduler.pipeline_executions e
            JOIN scheduler.pipeline_definitions d ON d.id = e.pipeline_id
            ORDER BY e.started_at DESC
            LIMIT ? OFFSET ?
            """,
            (rs, rowNum) -> new PipelineExecutionSummaryDto(
                (UUID) rs.getObject("id"), rs.getString("pipeline_name"), rs.getString("status"),
                toInstant(rs.getTimestamp("started_at")), toInstant(rs.getTimestamp("finished_at")),
                rs.getInt("rows_read"), rs.getInt("rows_accepted"), rs.getInt("rows_rejected")
            ),
            size, page * size
        );

        return PageResponse.of(content, page, size, totalElements);
    }

    public Optional<PipelineExecutionDetailDto> findExecutionDetail(UUID id) {
        List<PipelineExecutionDetailDto> rows = jdbcTemplate.query(
            """
            SELECT e.id, d.name AS pipeline_name, e.status, e.started_at, e.finished_at,
                   e.rows_read, e.rows_accepted, e.rows_rejected, e.retry_count
            FROM scheduler.pipeline_executions e
            JOIN scheduler.pipeline_definitions d ON d.id = e.pipeline_id
            WHERE e.id = ?
            """,
            (rs, rowNum) -> new PipelineExecutionDetailDto(
                (UUID) rs.getObject("id"), rs.getString("pipeline_name"), rs.getString("status"),
                toInstant(rs.getTimestamp("started_at")), toInstant(rs.getTimestamp("finished_at")),
                rs.getInt("rows_read"), rs.getInt("rows_accepted"), rs.getInt("rows_rejected"),
                rs.getInt("retry_count"), List.of(), null
            ),
            id
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }

        List<String> errors = jdbcTemplate.query(
            "SELECT message FROM scheduler.pipeline_execution_errors WHERE pipeline_execution_id = ? ORDER BY created_at",
            (rs, rowNum) -> rs.getString("message"), id
        );

        DataQualityScoreDto qualityScore = jdbcTemplate.query(
            """
            SELECT completeness, duplicate_rate, missing_field_rate, validation_error_rate, score
            FROM common.data_quality_scores WHERE pipeline_execution_id = ?
            """,
            (rs, rowNum) -> new DataQualityScoreDto(
                rs.getDouble("completeness"), rs.getDouble("duplicate_rate"),
                rs.getDouble("missing_field_rate"), rs.getDouble("validation_error_rate"), rs.getDouble("score")
            ),
            id
        ).stream().findFirst().orElse(null);

        PipelineExecutionDetailDto base = rows.get(0);
        return Optional.of(new PipelineExecutionDetailDto(
            base.id(), base.pipelineName(), base.status(), base.startedAt(), base.finishedAt(),
            base.rowsRead(), base.rowsAccepted(), base.rowsRejected(), base.retryCount(), errors, qualityScore
        ));
    }

    private static java.time.Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
