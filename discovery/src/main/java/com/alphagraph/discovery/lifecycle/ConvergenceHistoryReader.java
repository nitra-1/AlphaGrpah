package com.alphagraph.discovery.lifecycle;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reads Stage 4's own output tables (never Stage 3 directly, never recalculates Stage 3/4 logic -
 * Stage 4 already normalized those domains). {@code discovery.convergence} is entirely
 * package-private (confirmed by direct read) - a sibling package genuinely cannot import its
 * records even inside the same Gradle module, so this reader owns its own raw SQL and its own
 * DTOs, exactly mirroring how {@code discovery.convergence}'s own 5 domain-sequence readers
 * already read Stage 3's package-private records via raw SQL, one layer up.
 *
 * <p><b>{@code discovery.convergence_reasons} is deliberately not read in v1</b> - every field
 * Stage 5's rules need (state, scores, domain coverage, per-domain status/strength/confidence/
 * representative sequence type) is already on {@code convergence_snapshots}/
 * {@code convergence_domain_contributions}. A disclosed scoping decision, not a silent gap - a
 * third query can be added later if a rule ever needs a specific reason code.
 *
 * <p>2 queries total, not N+1: one bounded fetch of snapshots, one batched {@code snapshot_id IN
 * (...)} fetch of their domain contributions, grouped and zipped in Java - the same
 * dynamic-placeholder idiom {@code DiscoveryConvergenceRuleSetLoader} already uses for its own
 * {@code RULE_NAMES} list.
 */
@Component
class ConvergenceHistoryReader {

    private static final String SNAPSHOT_COLUMNS = """
        id, as_of_date, convergence_state, pre_contradiction_state, convergence_score,
        raw_convergence_score, contradiction_penalty, active_domain_count, domain_coverage_count,
        breadth_score, maturity_score, recency_score, confidence_score, density_score,
        readiness, earliest_supporting_date, latest_supporting_date
        """;

    private static final String CONTRIBUTION_COLUMNS = """
        snapshot_id, domain, contribution_status, strongest_phase, domain_strength,
        domain_confidence, contribution_score, evidence_reference
        """;

    private final JdbcTemplate jdbcTemplate;

    ConvergenceHistoryReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Ascending by {@code asOfDate} - always {@code ORDER BY as_of_date DESC LIMIT ?} first, then reversed, never fetch the oldest {@code limit} rows and truncate. */
    List<ConvergenceSnapshotRow> findHistory(UUID instrumentId, LocalDate upToInclusive, int limit) {
        List<Map<String, Object>> snapshotRows = jdbcTemplate.queryForList(
            "SELECT " + SNAPSHOT_COLUMNS + " FROM discovery.convergence_snapshots " +
            "WHERE instrument_id = ? AND as_of_date <= ? ORDER BY as_of_date DESC LIMIT ?",
            instrumentId, Date.valueOf(upToInclusive), limit
        );
        if (snapshotRows.isEmpty()) {
            return List.of();
        }

        List<UUID> snapshotIds = snapshotRows.stream().map(r -> (UUID) r.get("id")).toList();
        Map<UUID, List<DomainContributionRow>> contributionsBySnapshot = findContributions(snapshotIds);

        List<ConvergenceSnapshotRow> descending = new ArrayList<>();
        for (Map<String, Object> row : snapshotRows) {
            UUID snapshotId = (UUID) row.get("id");
            descending.add(new ConvergenceSnapshotRow(
                ((Date) row.get("as_of_date")).toLocalDate(),
                (String) row.get("convergence_state"), (String) row.get("pre_contradiction_state"),
                toDouble(row.get("convergence_score")), toDouble(row.get("raw_convergence_score")), toDouble(row.get("contradiction_penalty")),
                ((Number) row.get("active_domain_count")).intValue(), ((Number) row.get("domain_coverage_count")).intValue(),
                toDouble(row.get("breadth_score")), toDouble(row.get("maturity_score")), toDouble(row.get("recency_score")),
                toDouble(row.get("confidence_score")), toDouble(row.get("density_score")),
                (String) row.get("readiness"),
                toLocalDate(row.get("earliest_supporting_date")), toLocalDate(row.get("latest_supporting_date")),
                contributionsBySnapshot.getOrDefault(snapshotId, List.of())
            ));
        }
        List<ConvergenceSnapshotRow> ascending = new ArrayList<>(descending);
        Collections.reverse(ascending);
        return ascending;
    }

    private Map<UUID, List<DomainContributionRow>> findContributions(List<UUID> snapshotIds) {
        String placeholders = String.join(",", snapshotIds.stream().map(id -> "?").toList());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT " + CONTRIBUTION_COLUMNS + " FROM discovery.convergence_domain_contributions WHERE snapshot_id IN (" + placeholders + ")",
            snapshotIds.toArray()
        );
        Map<UUID, List<DomainContributionRow>> bySnapshot = new HashMap<>();
        for (Map<String, Object> row : rows) {
            UUID snapshotId = (UUID) row.get("snapshot_id");
            bySnapshot.computeIfAbsent(snapshotId, k -> new ArrayList<>()).add(new DomainContributionRow(
                (String) row.get("domain"), (String) row.get("contribution_status"), (String) row.get("strongest_phase"),
                toDouble(row.get("domain_strength")), toDouble(row.get("domain_confidence")), toDouble(row.get("contribution_score")),
                (String) row.get("evidence_reference")
            ));
        }
        return bySnapshot;
    }

    private static Double toDouble(Object value) {
        return value == null ? null : ((Number) value).doubleValue();
    }

    private static LocalDate toLocalDate(Object value) {
        return value == null ? null : ((Date) value).toLocalDate();
    }
}
