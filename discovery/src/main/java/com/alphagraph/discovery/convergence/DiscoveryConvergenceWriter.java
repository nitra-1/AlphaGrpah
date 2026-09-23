package com.alphagraph.discovery.convergence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.util.UUID;

/**
 * Upserts one {@code (instrument_id, as_of_date)} row into {@code discovery.convergence_snapshots},
 * then deletes+reinserts both {@code convergence_domain_contributions} (always exactly the 5
 * {@link ConvergenceDomain} values) and {@code convergence_reasons} - same convention every
 * Stage 2/3 writer in this codebase already uses. All 8 score/penalty columns are passed through
 * as boxed {@code Double}/nullable - {@code JdbcTemplate} binds a Java {@code null} to SQL
 * {@code NULL} directly, no special-casing needed.
 */
@Component
class DiscoveryConvergenceWriter {

    private static final int RULE_VERSION = 1;

    private final JdbcTemplate jdbcTemplate;

    DiscoveryConvergenceWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void write(ConvergenceResult result) {
        UUID snapshotId = jdbcTemplate.query(
            """
            INSERT INTO discovery.convergence_snapshots (
                id, instrument_id, symbol, as_of_date, convergence_state, pre_contradiction_state,
                active_domain_count, qualifying_sequence_count, domain_coverage_count, domain_coverage_pct,
                breadth_score, maturity_score, recency_score, confidence_score, density_score,
                raw_convergence_score, contradiction_penalty, convergence_score,
                earliest_supporting_date, latest_supporting_date, readiness, rule_version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id, as_of_date) DO UPDATE SET
                symbol = EXCLUDED.symbol, convergence_state = EXCLUDED.convergence_state,
                pre_contradiction_state = EXCLUDED.pre_contradiction_state,
                active_domain_count = EXCLUDED.active_domain_count, qualifying_sequence_count = EXCLUDED.qualifying_sequence_count,
                domain_coverage_count = EXCLUDED.domain_coverage_count, domain_coverage_pct = EXCLUDED.domain_coverage_pct,
                breadth_score = EXCLUDED.breadth_score, maturity_score = EXCLUDED.maturity_score,
                recency_score = EXCLUDED.recency_score, confidence_score = EXCLUDED.confidence_score, density_score = EXCLUDED.density_score,
                raw_convergence_score = EXCLUDED.raw_convergence_score, contradiction_penalty = EXCLUDED.contradiction_penalty,
                convergence_score = EXCLUDED.convergence_score, earliest_supporting_date = EXCLUDED.earliest_supporting_date,
                latest_supporting_date = EXCLUDED.latest_supporting_date, readiness = EXCLUDED.readiness, rule_version = EXCLUDED.rule_version
            RETURNING id
            """,
            (rs, rowNum) -> (UUID) rs.getObject("id"),
            UUID.randomUUID(), result.instrumentId(), result.symbol(), Date.valueOf(result.asOfDate()),
            result.convergenceState().name(), result.preContradictionState() == null ? null : result.preContradictionState().name(),
            result.activeDomainCount(), result.qualifyingSequenceCount(), result.domainCoverageCount(), result.domainCoveragePct(),
            result.breadthScore(), result.maturityScore(), result.recencyScore(), result.confidenceScore(), result.densityScore(),
            result.rawConvergenceScore(), result.contradictionPenalty(), result.convergenceScore(),
            result.earliestSupportingDate() == null ? null : Date.valueOf(result.earliestSupportingDate()),
            result.latestSupportingDate() == null ? null : Date.valueOf(result.latestSupportingDate()),
            result.readiness().name(), RULE_VERSION
        ).get(0);

        jdbcTemplate.update("DELETE FROM discovery.convergence_domain_contributions WHERE snapshot_id = ?", snapshotId);
        for (DomainContribution dc : result.contributions()) {
            jdbcTemplate.update(
                """
                INSERT INTO discovery.convergence_domain_contributions (
                    id, snapshot_id, domain, contribution_status, active_sequence_count, strongest_phase,
                    domain_strength, domain_confidence, earliest_sequence_date, latest_sequence_date,
                    contribution_score, evidence_reference
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), snapshotId, dc.domain().name(), dc.status().name(), dc.activeSequenceCount(),
                dc.strongestPhase() == null ? null : dc.strongestPhase().name(),
                dc.domainStrength(), dc.domainConfidence(),
                dc.earliestSequenceDate() == null ? null : Date.valueOf(dc.earliestSequenceDate()),
                dc.latestSequenceDate() == null ? null : Date.valueOf(dc.latestSequenceDate()),
                dc.contributionScore(),
                dc.sequences().isEmpty() ? null : dc.sequences().get(0).sequenceType()
            );
        }

        jdbcTemplate.update("DELETE FROM discovery.convergence_reasons WHERE snapshot_id = ?", snapshotId);
        for (ReasonCode reason : result.reasons()) {
            jdbcTemplate.update(
                "INSERT INTO discovery.convergence_reasons (id, snapshot_id, reason_code, domain, sequence_type, metric_value, evidence_date, evidence_reference) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), snapshotId, reason.code(), reason.domain(), reason.sequenceType(),
                reason.metricValue(), reason.evidenceDate() == null ? null : Date.valueOf(reason.evidenceDate()), reason.evidenceReference()
            );
        }
    }
}
