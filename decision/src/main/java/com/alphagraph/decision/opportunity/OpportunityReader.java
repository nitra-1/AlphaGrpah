package com.alphagraph.decision.opportunity;

import com.alphagraph.decision.api.ConvergenceSnapshot;
import com.alphagraph.decision.api.DomainContribution;
import com.alphagraph.decision.api.LifecycleSnapshot;
import com.alphagraph.decision.api.LifecycleTransition;
import com.alphagraph.decision.api.OpportunityDomainDetail;
import com.alphagraph.decision.api.ReasonNote;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads Stage 4/5's own output tables ({@code discovery.convergence_snapshots}/
 * {@code _domain_contributions}/{@code _reasons} and {@code discovery.lifecycle_snapshots}/
 * {@code _transitions}/{@code _reasons}) - {@code discovery.lifecycle}/{@code discovery.convergence}
 * are entirely package-private (confirmed by direct read), so this owns its own raw SQL and its
 * own DTOs, exactly mirroring how {@code discovery.lifecycle} itself already reads Stage 4's
 * package-private tables one layer down. Stage 1-3 (per-domain evidence/inflection/sequences) live
 * in the five domain readers alongside this one - see {@link OpportunityDomainDetailAssembler}.
 */
@Component
public class OpportunityReader {

    private static final String LIFECYCLE_COLUMNS = """
        id, instrument_id, symbol, as_of_date, lifecycle_state, lifecycle_readiness,
        trajectory_direction, peak_lifecycle_stage, lifecycle_strength, trajectory_score,
        current_convergence_score, peak_convergence_score, current_active_domains, peak_active_domains,
        lifecycle_started_date, state_started_date, lifecycle_age_days, computed_at
        """;

    private static final String CONVERGENCE_COLUMNS = """
        id, instrument_id, symbol, as_of_date, convergence_state, pre_contradiction_state,
        active_domain_count, domain_coverage_count, domain_coverage_pct, convergence_score, readiness
        """;

    private static final String CONTRIBUTION_COLUMNS = """
        domain, contribution_status, active_sequence_count, strongest_phase,
        domain_strength, domain_confidence, contribution_score, evidence_reference
        """;

    private final JdbcTemplate jdbcTemplate;
    private final OpportunityDomainDetailAssembler domainDetailAssembler;

    public OpportunityReader(JdbcTemplate jdbcTemplate, OpportunityDomainDetailAssembler domainDetailAssembler) {
        this.jdbcTemplate = jdbcTemplate;
        this.domainDetailAssembler = domainDetailAssembler;
    }

    /**
     * The full Stage 1-3 causal chain for all 5 domains, anchored to {@code asOfDate} - the
     * caller must pass the displayed Stage 4 convergence snapshot's own {@code asOfDate}, never
     * {@code LocalDate.now()}, so a later evidence/inflection/sequence row can never leak into an
     * earlier snapshot's explanation. {@code convergenceContributions} is the same list already
     * returned by {@link #findLatestConvergence}, passed in rather than re-fetched.
     */
    public List<OpportunityDomainDetail> findDomainDetails(UUID instrumentId, LocalDate asOfDate, List<DomainContribution> convergenceContributions) {
        return domainDetailAssembler.assemble(instrumentId, asOfDate, convergenceContributions);
    }

    /** Every instrument's latest lifecycle snapshot - powers the Opportunities dashboard. */
    public List<LifecycleSnapshot> findAllLatestLifecycle() {
        return jdbcTemplate.query(
            "SELECT DISTINCT ON (instrument_id) " + LIFECYCLE_COLUMNS +
            " FROM discovery.lifecycle_snapshots ORDER BY instrument_id, as_of_date DESC",
            OpportunityReader::mapLifecycle
        );
    }

    public Optional<LifecycleSnapshot> findLatestLifecycle(UUID instrumentId) {
        List<LifecycleSnapshot> rows = jdbcTemplate.query(
            "SELECT " + LIFECYCLE_COLUMNS + " FROM discovery.lifecycle_snapshots WHERE instrument_id = ? ORDER BY as_of_date DESC LIMIT 1",
            OpportunityReader::mapLifecycle, instrumentId
        );
        return rows.stream().findFirst();
    }

    public List<ReasonNote> findLifecycleReasons(UUID instrumentId) {
        return findReasonsForLatest(
            instrumentId,
            "SELECT id FROM discovery.lifecycle_snapshots WHERE instrument_id = ? ORDER BY as_of_date DESC LIMIT 1",
            "SELECT reason_code, metric_name, metric_value, evidence_date, evidence_reference FROM discovery.lifecycle_reasons WHERE snapshot_id = ?"
        );
    }

    public List<LifecycleTransition> findTransitions(UUID instrumentId) {
        return jdbcTemplate.query(
            """
            SELECT instrument_id, symbol, transition_date, from_state, to_state, trigger_reason, convergence_score, active_domain_count
            FROM discovery.lifecycle_transitions WHERE instrument_id = ? ORDER BY transition_date DESC
            """,
            (rs, rowNum) -> new LifecycleTransition(
                (UUID) rs.getObject("instrument_id"), rs.getString("symbol"), rs.getDate("transition_date").toLocalDate(),
                rs.getString("from_state"), rs.getString("to_state"), rs.getString("trigger_reason"),
                toDouble(rs.getObject("convergence_score")), (Integer) rs.getObject("active_domain_count")
            ),
            instrumentId
        );
    }

    public Optional<ConvergenceSnapshot> findLatestConvergence(UUID instrumentId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT " + CONVERGENCE_COLUMNS + " FROM discovery.convergence_snapshots WHERE instrument_id = ? ORDER BY as_of_date DESC LIMIT 1",
            instrumentId
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> row = rows.get(0);
        UUID snapshotId = (UUID) row.get("id");
        List<DomainContribution> contributions = jdbcTemplate.query(
            "SELECT " + CONTRIBUTION_COLUMNS + " FROM discovery.convergence_domain_contributions WHERE snapshot_id = ?",
            OpportunityReader::mapContribution, snapshotId
        );
        return Optional.of(new ConvergenceSnapshot(
            (UUID) row.get("instrument_id"), (String) row.get("symbol"), ((Date) row.get("as_of_date")).toLocalDate(),
            (String) row.get("convergence_state"), (String) row.get("pre_contradiction_state"),
            ((Number) row.get("active_domain_count")).intValue(), ((Number) row.get("domain_coverage_count")).intValue(),
            ((Number) row.get("domain_coverage_pct")).intValue(),
            toDouble(row.get("convergence_score")), (String) row.get("readiness"),
            contributions
        ));
    }

    public List<ReasonNote> findConvergenceReasons(UUID instrumentId) {
        return findReasonsForLatest(
            instrumentId,
            "SELECT id FROM discovery.convergence_snapshots WHERE instrument_id = ? ORDER BY as_of_date DESC LIMIT 1",
            "SELECT reason_code, NULL AS metric_name, metric_value, evidence_date, evidence_reference FROM discovery.convergence_reasons WHERE snapshot_id = ?"
        );
    }

    private List<ReasonNote> findReasonsForLatest(UUID instrumentId, String findSnapshotIdSql, String findReasonsSql) {
        List<UUID> snapshotIds = jdbcTemplate.query(findSnapshotIdSql, (rs, rowNum) -> (UUID) rs.getObject("id"), instrumentId);
        if (snapshotIds.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query(findReasonsSql, OpportunityReader::mapReason, snapshotIds.get(0));
    }

    private static LifecycleSnapshot mapLifecycle(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new LifecycleSnapshot(
            (UUID) rs.getObject("instrument_id"), rs.getString("symbol"), rs.getDate("as_of_date").toLocalDate(),
            rs.getString("lifecycle_state"), rs.getString("lifecycle_readiness"), rs.getString("trajectory_direction"), rs.getString("peak_lifecycle_stage"),
            toDouble(rs.getObject("lifecycle_strength")), toDouble(rs.getObject("trajectory_score")),
            toDouble(rs.getObject("current_convergence_score")), toDouble(rs.getObject("peak_convergence_score")),
            (Integer) rs.getObject("current_active_domains"), (Integer) rs.getObject("peak_active_domains"),
            toLocalDate(rs.getDate("lifecycle_started_date")), toLocalDate(rs.getDate("state_started_date")), (Integer) rs.getObject("lifecycle_age_days"),
            ((Timestamp) rs.getObject("computed_at")).toInstant()
        );
    }

    private static DomainContribution mapContribution(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new DomainContribution(
            rs.getString("domain"), rs.getString("contribution_status"), rs.getInt("active_sequence_count"), rs.getString("strongest_phase"),
            toDouble(rs.getObject("domain_strength")), toDouble(rs.getObject("domain_confidence")), toDouble(rs.getObject("contribution_score")),
            rs.getString("evidence_reference")
        );
    }

    static ReasonNote mapReason(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new ReasonNote(
            rs.getString("reason_code"), rs.getString("metric_name"), toDouble(rs.getObject("metric_value")),
            toLocalDate(rs.getDate("evidence_date")), rs.getString("evidence_reference")
        );
    }

    static Double toDouble(Object value) {
        return value == null ? null : ((Number) value).doubleValue();
    }

    static LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }
}
