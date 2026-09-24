package com.alphagraph.discovery.lifecycle;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Upserts one {@code (instrument_id, as_of_date)} row into {@code discovery.lifecycle_snapshots},
 * deletes+reinserts {@code lifecycle_reasons} - same convention every Stage 2/3/4 writer already
 * uses. Transition handling (round-2 correction 3): **always** deletes any existing {@code
 * lifecycle_transitions} row for {@code (instrument_id, as_of_date)} first (backed by {@code
 * UNIQUE(instrument_id, transition_date)}), then inserts the current authoritative transition only
 * if the emission rule is satisfied - a same-day rerun with fresher upstream data *replaces* the
 * prior candidate transition rather than accumulating a second row for the date.
 *
 * <p>Emission rule, evaluated here (not the engine - {@link LifecycleResult}'s shape stays exactly
 * the record, no extra "did it transition" field): a transition is written only when {@code
 * previousLifecycle} is present, the new result's readiness is {@code READY}, and its {@code
 * lifecycleState} differs from {@code previousLifecycle}'s. A {@code READY→NULL} readiness drop is
 * never a transition - there is no new authoritative state to compare. The very first-ever
 * authoritative classification ({@code previousLifecycle} absent, new result {@code READY}) is
 * recorded with {@code from_state = NULL} and {@code trigger_reason =
 * 'INITIAL_LIFECYCLE_CLASSIFICATION'}, distinguished from an ordinary transition.
 */
@Component
class DiscoveryLifecycleWriter {

    private static final int RULE_VERSION = 1;

    private final JdbcTemplate jdbcTemplate;

    DiscoveryLifecycleWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void write(LifecycleResult result, Optional<LifecycleSnapshotRow> previousLifecycle) {
        UUID snapshotId = jdbcTemplate.query(
            """
            INSERT INTO discovery.lifecycle_snapshots (
                id, instrument_id, symbol, as_of_date, lifecycle_state, lifecycle_readiness,
                trajectory_direction, peak_lifecycle_stage, lifecycle_strength, trajectory_score,
                current_convergence_score, peak_convergence_score, current_active_domains, peak_active_domains,
                lifecycle_started_date, state_started_date, lifecycle_age_days, rule_version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id, as_of_date) DO UPDATE SET
                symbol = EXCLUDED.symbol, lifecycle_state = EXCLUDED.lifecycle_state, lifecycle_readiness = EXCLUDED.lifecycle_readiness,
                trajectory_direction = EXCLUDED.trajectory_direction, peak_lifecycle_stage = EXCLUDED.peak_lifecycle_stage,
                lifecycle_strength = EXCLUDED.lifecycle_strength, trajectory_score = EXCLUDED.trajectory_score,
                current_convergence_score = EXCLUDED.current_convergence_score, peak_convergence_score = EXCLUDED.peak_convergence_score,
                current_active_domains = EXCLUDED.current_active_domains, peak_active_domains = EXCLUDED.peak_active_domains,
                lifecycle_started_date = EXCLUDED.lifecycle_started_date, state_started_date = EXCLUDED.state_started_date,
                lifecycle_age_days = EXCLUDED.lifecycle_age_days, rule_version = EXCLUDED.rule_version
            RETURNING id
            """,
            (rs, rowNum) -> (UUID) rs.getObject("id"),
            UUID.randomUUID(), result.instrumentId(), result.symbol(), Date.valueOf(result.asOfDate()),
            result.lifecycleState() == null ? null : result.lifecycleState().name(), result.readiness().name(),
            result.trajectoryDirection().name(), result.peakLifecycleStage() == null ? null : result.peakLifecycleStage().name(),
            result.lifecycleStrength(), result.trajectoryScore(),
            result.currentConvergenceScore(), result.peakConvergenceScore(),
            result.currentActiveDomains(), result.peakActiveDomains(),
            result.lifecycleStartedDate() == null ? null : Date.valueOf(result.lifecycleStartedDate()),
            result.stateStartedDate() == null ? null : Date.valueOf(result.stateStartedDate()),
            result.lifecycleAgeDays(), RULE_VERSION
        ).get(0);

        jdbcTemplate.update("DELETE FROM discovery.lifecycle_reasons WHERE snapshot_id = ?", snapshotId);
        for (LifecycleReason reason : result.reasons()) {
            jdbcTemplate.update(
                "INSERT INTO discovery.lifecycle_reasons (id, snapshot_id, reason_code, metric_name, metric_value, evidence_date, evidence_reference) VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), snapshotId, reason.code(), reason.metricName(), reason.metricValue(),
                reason.evidenceDate() == null ? null : Date.valueOf(reason.evidenceDate()), reason.evidenceReference()
            );
        }

        jdbcTemplate.update("DELETE FROM discovery.lifecycle_transitions WHERE instrument_id = ? AND transition_date = ?", result.instrumentId(), Date.valueOf(result.asOfDate()));
        writeTransitionIfApplicable(result, previousLifecycle);
    }

    private void writeTransitionIfApplicable(LifecycleResult result, Optional<LifecycleSnapshotRow> previousLifecycle) {
        if (result.readiness() != LifecycleReadiness.READY) {
            return; // readiness loss/recovery is never itself a transition - nothing new to compare
        }
        if (previousLifecycle.isEmpty()) {
            insertTransition(result, null, "INITIAL_LIFECYCLE_CLASSIFICATION");
            return;
        }
        LifecycleState previousState = previousLifecycle.get().lifecycleState();
        if (previousState == result.lifecycleState()) {
            return; // unchanged - no transition
        }
        insertTransition(result, previousState, triggerReasonFor(result.lifecycleState()));
    }

    private void insertTransition(LifecycleResult result, LifecycleState fromState, String triggerReason) {
        jdbcTemplate.update(
            "INSERT INTO discovery.lifecycle_transitions (id, instrument_id, symbol, transition_date, from_state, to_state, trigger_reason, convergence_score, active_domain_count, evidence_reference) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), result.instrumentId(), result.symbol(), Date.valueOf(result.asOfDate()),
            fromState == null ? null : fromState.name(), result.lifecycleState().name(), triggerReason,
            result.currentConvergenceScore(), result.currentActiveDomains(), null
        );
    }

    private static String triggerReasonFor(LifecycleState state) {
        return switch (state) {
            case ACCELERATING -> "LIFECYCLE_ACCELERATION_DETECTED";
            case DETERIORATING -> "LIFECYCLE_DETERIORATION_DETECTED";
            case MARKET_RECOGNITION -> "MARKET_RECOGNITION_PRESENT";
            case MATURE_RERATING -> "MATURE_RERATING_DURATION_REACHED";
            case EMERGING -> "MULTI_DOMAIN_INFLECTION_PERSISTED";
            case EARLY_INFLECTION -> "EARLY_CONVERGENCE_PERSISTED";
            case DORMANT -> "READY_HISTORY_ESTABLISHED";
        };
    }
}
