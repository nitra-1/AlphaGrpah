package com.alphagraph.discovery.lifecycle;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDate;

/**
 * Reads Stage 5's own previously-persisted classifications back. The single method here -
 * {@code findLatestAuthoritativeBefore} - is deliberately the *only* previous-state lookup Stage 5
 * needs: Stage 5 legitimately writes {@code INSUFFICIENT_HISTORY}/{@code NULL} rows during
 * temporary data gaps, so a naive "latest row regardless of readiness" lookup would let a single
 * bad day erase the company's real lifecycle history for hysteresis, deterioration-eligibility,
 * lifecycle-start-date tracking, and cycle-peak tracking. This reader skips straight past any gap
 * to the last row where {@code lifecycle_readiness = 'READY' AND lifecycle_state IS NOT NULL}.
 */
@Component
class LifecycleSnapshotReader {

    private static final String COLUMNS = """
        as_of_date, lifecycle_state, trajectory_direction, peak_lifecycle_stage,
        lifecycle_started_date, state_started_date,
        current_convergence_score, peak_convergence_score, current_active_domains, peak_active_domains
        """;

    private final JdbcTemplate jdbcTemplate;

    LifecycleSnapshotReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Strictly {@code as_of_date < ?} (not {@code <=}) - a same-day rerun always compares against the last real committed classification, never today's not-yet-written one. */
    Optional<LifecycleSnapshotRow> findLatestAuthoritativeBefore(UUID instrumentId, LocalDate asOfDate) {
        List<LifecycleSnapshotRow> rows = jdbcTemplate.query(
            "SELECT " + COLUMNS + " FROM discovery.lifecycle_snapshots " +
            "WHERE instrument_id = ? AND lifecycle_readiness = 'READY' AND lifecycle_state IS NOT NULL AND as_of_date < ? " +
            "ORDER BY as_of_date DESC LIMIT 1",
            (rs, rowNum) -> new LifecycleSnapshotRow(
                rs.getDate("as_of_date").toLocalDate(),
                LifecycleState.valueOf(rs.getString("lifecycle_state")),
                TrajectoryDirection.valueOf(rs.getString("trajectory_direction")),
                rs.getString("peak_lifecycle_stage") == null ? null : LifecycleState.valueOf(rs.getString("peak_lifecycle_stage")),
                rs.getDate("lifecycle_started_date") == null ? null : rs.getDate("lifecycle_started_date").toLocalDate(),
                rs.getDate("state_started_date") == null ? null : rs.getDate("state_started_date").toLocalDate(),
                rs.getObject("current_convergence_score") == null ? null : rs.getDouble("current_convergence_score"),
                rs.getObject("peak_convergence_score") == null ? null : rs.getDouble("peak_convergence_score"),
                rs.getObject("current_active_domains") == null ? null : rs.getInt("current_active_domains"),
                rs.getObject("peak_active_domains") == null ? null : rs.getInt("peak_active_domains")
            ),
            instrumentId, Date.valueOf(asOfDate)
        );
        return rows.stream().findFirst();
    }
}
