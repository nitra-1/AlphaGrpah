package com.alphagraph.decision.opportunity;

import com.alphagraph.decision.api.ReasonNote;
import com.alphagraph.decision.api.TransformationSequence;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * decision's own copy of {@code discovery.convergence.AbstractSequenceReader}'s exact shape - only
 * the schema name differs between the 5 domains, and this table family's shape (columns,
 * uniqueness) is identical everywhere. Point-in-time safe throughout: every query is bounded
 * {@code as_of_date <= ?}, never an unconditional "latest," so the Opportunity Detail page's
 * causal-chain drill-down can be anchored to the same date as the displayed Stage 4 convergence
 * snapshot rather than accidentally mixing in a later sequence row.
 */
abstract class AbstractDomainSequenceReader {

    private static final String SEQUENCE_COLUMNS = """
        id, sequence_type, sequence_phase, current_step, total_steps,
        first_step_date, last_step_date, sequence_strength, confidence
        """;

    private final JdbcTemplate jdbcTemplate;
    private final String sequencesTable;
    private final String reasonsTable;

    protected AbstractDomainSequenceReader(JdbcTemplate jdbcTemplate, String schema) {
        this.jdbcTemplate = jdbcTemplate;
        this.sequencesTable = schema + ".transformation_sequences";
        this.reasonsTable = schema + ".transformation_sequence_reasons";
    }

    /** One row per real {@code sequence_type}, each the most recent as of {@code asOfDate} - {@code DISTINCT ON}, never an unbounded latest. Mirrors {@code AbstractSequenceReader.findActiveSequencesAsOf} exactly. */
    List<TransformationSequence> findActiveSequences(UUID instrumentId, LocalDate asOfDate) {
        String sql = "SELECT DISTINCT ON (sequence_type) " + SEQUENCE_COLUMNS + " FROM " + sequencesTable
            + " WHERE instrument_id = ? AND as_of_date <= ? ORDER BY sequence_type, as_of_date DESC";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, instrumentId, Date.valueOf(asOfDate));
        if (rows.isEmpty()) {
            return List.of();
        }

        List<UUID> sequenceIds = rows.stream().map(r -> (UUID) r.get("id")).toList();
        Map<UUID, List<ReasonNote>> reasonsBySequence = findReasons(sequenceIds);

        List<TransformationSequence> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            UUID sequenceId = (UUID) row.get("id");
            result.add(new TransformationSequence(
                (String) row.get("sequence_type"), (String) row.get("sequence_phase"),
                ((Number) row.get("current_step")).intValue(), ((Number) row.get("total_steps")).intValue(),
                toLocalDate(row.get("first_step_date")), toLocalDate(row.get("last_step_date")),
                toDouble(row.get("sequence_strength")), toDouble(row.get("confidence")),
                reasonsBySequence.getOrDefault(sequenceId, List.of())
            ));
        }
        return result;
    }

    /** {@code reason_code, metric_value, evidence_reference} only - unlike discovery's own {@code lifecycle_reasons}/{@code convergence_reasons}, every domain's own {@code *_sequence_reasons}/{@code *_state_reasons} table has no {@code evidence_date} column (confirmed by direct read of the real migration SQL). */
    private Map<UUID, List<ReasonNote>> findReasons(List<UUID> sequenceIds) {
        String placeholders = String.join(",", sequenceIds.stream().map(id -> "?").toList());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT sequence_id, reason_code, metric_value, evidence_reference FROM " + reasonsTable
                + " WHERE sequence_id IN (" + placeholders + ")",
            sequenceIds.toArray()
        );
        Map<UUID, List<ReasonNote>> bySequence = new HashMap<>();
        for (Map<String, Object> row : rows) {
            UUID sequenceId = (UUID) row.get("sequence_id");
            bySequence.computeIfAbsent(sequenceId, k -> new ArrayList<>()).add(new ReasonNote(
                (String) row.get("reason_code"), null, toDouble(row.get("metric_value")),
                null, (String) row.get("evidence_reference")
            ));
        }
        return bySequence;
    }

    private static Double toDouble(Object value) {
        return value == null ? null : ((Number) value).doubleValue();
    }

    private static LocalDate toLocalDate(Object value) {
        return value == null ? null : ((Date) value).toLocalDate();
    }
}
