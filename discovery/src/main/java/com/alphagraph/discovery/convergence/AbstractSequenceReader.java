package com.alphagraph.discovery.convergence;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The 5 domain-specific readers ({@link MarketSequenceReader} etc.) share this exact SQL shape -
 * only the schema name and the readiness table's own history-coverage column name differ (
 * {@code history_sessions}/{@code history_periods}/{@code history_days} per domain, docs/008's own
 * naming). Unlike domain modules never sharing a type across module boundaries, this is ordinary
 * in-module code reuse - all 5 readers live inside {@code discovery} itself. Mirrors the exact
 * as-of-bounded pattern already established in {@code intelligence.riskcontradiction.
 * FinancialGrowthReader.findStateAsOf} - {@code as_of_date <= ?}, never an unbounded "latest."
 */
abstract class AbstractSequenceReader {

    private static final String SEQUENCE_COLUMNS = """
        sequence_type, sequence_phase, current_step, total_steps,
        first_step_date, last_step_date, sequence_strength, confidence, as_of_date
        """;

    private final JdbcTemplate jdbcTemplate;
    private final String sequencesTable;
    private final String readinessTable;
    private final String readinessCoverageColumn;

    protected AbstractSequenceReader(JdbcTemplate jdbcTemplate, String schema, String readinessCoverageColumn) {
        this.jdbcTemplate = jdbcTemplate;
        this.sequencesTable = schema + ".transformation_sequences";
        this.readinessTable = schema + ".transformation_sequence_readiness";
        this.readinessCoverageColumn = readinessCoverageColumn;
    }

    /** One row per real {@code sequence_type} for this domain, each the most recent as-of {@code asOfDate} - {@code DISTINCT ON}, never an unbounded latest. */
    List<SequenceRow> findActiveSequencesAsOf(UUID instrumentId, LocalDate asOfDate) {
        String sql = "SELECT DISTINCT ON (sequence_type) " + SEQUENCE_COLUMNS + " FROM " + sequencesTable
            + " WHERE instrument_id = ? AND as_of_date <= ? ORDER BY sequence_type, as_of_date DESC";
        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new SequenceRow(
                rs.getString("sequence_type"),
                SequencePhase.valueOf(rs.getString("sequence_phase")),
                rs.getInt("current_step"), rs.getInt("total_steps"),
                toLocalDateOrNull(rs.getDate("first_step_date")), toLocalDateOrNull(rs.getDate("last_step_date")),
                rs.getDouble("sequence_strength"), rs.getDouble("confidence"),
                rs.getDate("as_of_date").toLocalDate()
            ),
            instrumentId, Date.valueOf(asOfDate)
        );
    }

    Optional<ReadinessRow> findReadinessAsOf(UUID instrumentId, LocalDate asOfDate) {
        String sql = "SELECT " + readinessCoverageColumn + ", readiness FROM " + readinessTable
            + " WHERE instrument_id = ? AND as_of_date <= ? ORDER BY as_of_date DESC LIMIT 1";
        List<ReadinessRow> rows = jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new ReadinessRow(SourceReadiness.valueOf(rs.getString("readiness")), rs.getInt(readinessCoverageColumn)),
            instrumentId, Date.valueOf(asOfDate)
        );
        return rows.stream().findFirst();
    }

    private static LocalDate toLocalDateOrNull(Date date) {
        return date == null ? null : date.toLocalDate();
    }
}
