package com.alphagraph.ownership.transformation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Reads {@code ownership.transformation_states}/{@code _state_reasons} history for Stage 3, merged
 * with each of the PROMOTER/FII/DII metrics' own {@link OwnershipEvidenceObservation} history.
 *
 * <p><b>Canonicalizes by distinct {@code latest_period_end}, never raw state rows</b> (Correction 1):
 * more than one real {@code as_of_date} row can exist for the same quarter (the already-disclosed
 * residual gap from the {@code as_of_date} fix in {@code OwnershipTransformationEngine} - the
 * writer's upsert key is {@code (instrument_id, as_of_date)}, not {@code (instrument_id,
 * latest_period_end)}, so a mid-quarter XBRL advance can produce a second row for the same real
 * quarter). Walking raw rows would let one real quarter count as two reporting periods, inflating
 * every gap/persistence/contradiction/age counter downstream. For each {@code latest_period_end},
 * this reader picks the row with the latest {@code as_of_date} that still satisfies the point-in-time
 * bound - "the latest valid Stage 2 state whose {@code as_of_date <= evaluationAsOfDate}" - so the
 * walked history is exactly one entry per distinct quarter.
 *
 * <p><b>Evidence join is exact-{@code period_end}-only, never a silent older fallback</b>
 * (Correction 3): a reason that fired in a given quarter's state row is guaranteed by Stage 2's own
 * write path to have real evidence at that exact {@code period_end} (the metric had a value that
 * quarter, which is exactly when {@code TransformationEvidenceWriter} wrote it) - so falling back to
 * an older observation would only ever mask a genuine data-integrity gap, never legitimately apply.
 * A missing exact match resolves to {@code null} (handled defensively downstream, same convention
 * {@code market.transformation.MarketTransformationSequenceEngine.fromObservation} already uses for
 * its own "should never happen" case).
 */
@Component
class OwnershipInflectionHistoryReader {

    private final JdbcTemplate jdbcTemplate;
    private final OwnershipTransformationEvidenceReader evidenceReader;

    OwnershipInflectionHistoryReader(JdbcTemplate jdbcTemplate, OwnershipTransformationEvidenceReader evidenceReader) {
        this.jdbcTemplate = jdbcTemplate;
        this.evidenceReader = evidenceReader;
    }

    List<OwnershipInflectionHistoryEntry> findHistory(UUID instrumentId, LocalDate upToInclusiveOrNull, int limit) {
        String sql = """
            SELECT id, symbol, as_of_date, latest_period_end, driving_metric FROM (
                SELECT ts.*, ROW_NUMBER() OVER (PARTITION BY ts.latest_period_end ORDER BY ts.as_of_date DESC) AS rn
                FROM ownership.transformation_states ts
                WHERE ts.instrument_id = ?
            """ + (upToInclusiveOrNull == null ? "" : "  AND ts.as_of_date <= ?\n") + """
            ) ranked
            WHERE rn = 1
            ORDER BY latest_period_end DESC
            LIMIT ?
            """;
        List<Object> args = new ArrayList<>();
        args.add(instrumentId);
        if (upToInclusiveOrNull != null) {
            args.add(Date.valueOf(upToInclusiveOrNull));
        }
        args.add(limit);

        List<CanonicalStateRow> descendingByQuarter = jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new CanonicalStateRow(
                (UUID) rs.getObject("id"), rs.getString("symbol"), rs.getDate("as_of_date").toLocalDate(),
                rs.getDate("latest_period_end").toLocalDate(),
                rs.getString("driving_metric") == null ? null : TransformationMetric.valueOf(rs.getString("driving_metric"))
            ),
            args.toArray()
        );
        if (descendingByQuarter.isEmpty()) {
            return List.of();
        }

        Map<UUID, Set<String>> reasonsByStateId = new HashMap<>();
        for (CanonicalStateRow row : descendingByQuarter) {
            reasonsByStateId.put(row.id(), new HashSet<>());
        }
        List<UUID> stateIds = descendingByQuarter.stream().map(CanonicalStateRow::id).toList();
        String placeholders = String.join(",", Collections.nCopies(stateIds.size(), "?"));
        jdbcTemplate.query(
            "SELECT state_id, reason_code FROM ownership.transformation_state_reasons WHERE state_id IN (" + placeholders + ")",
            rs -> {
                UUID stateId = (UUID) rs.getObject("state_id");
                reasonsByStateId.get(stateId).add(rs.getString("reason_code"));
            },
            stateIds.toArray()
        );

        LocalDate newestPeriodEnd = descendingByQuarter.get(0).latestPeriodEnd();
        int evidenceLimit = descendingByQuarter.size() + 10;
        Map<LocalDate, OwnershipEvidenceObservation> promoterByPeriod = exactByPeriodEnd(evidenceReader.findHistory(instrumentId, TransformationMetric.PROMOTER, newestPeriodEnd, evidenceLimit));
        Map<LocalDate, OwnershipEvidenceObservation> fiiByPeriod = exactByPeriodEnd(evidenceReader.findHistory(instrumentId, TransformationMetric.FII, newestPeriodEnd, evidenceLimit));
        Map<LocalDate, OwnershipEvidenceObservation> diiByPeriod = exactByPeriodEnd(evidenceReader.findHistory(instrumentId, TransformationMetric.DII, newestPeriodEnd, evidenceLimit));

        List<OwnershipInflectionHistoryEntry> ascending = new ArrayList<>();
        for (int i = descendingByQuarter.size() - 1; i >= 0; i--) {
            CanonicalStateRow row = descendingByQuarter.get(i);
            ascending.add(new OwnershipInflectionHistoryEntry(
                row.asOfDate(), row.latestPeriodEnd(), row.symbol(), reasonsByStateId.get(row.id()), row.drivingMetric(),
                promoterByPeriod.get(row.latestPeriodEnd()), fiiByPeriod.get(row.latestPeriodEnd()), diiByPeriod.get(row.latestPeriodEnd())
            ));
        }
        return ascending;
    }

    private static Map<LocalDate, OwnershipEvidenceObservation> exactByPeriodEnd(List<OwnershipEvidenceObservation> evidenceAscending) {
        Map<LocalDate, OwnershipEvidenceObservation> byPeriodEnd = new HashMap<>();
        for (OwnershipEvidenceObservation observation : evidenceAscending) {
            byPeriodEnd.put(observation.periodEnd(), observation);
        }
        return byPeriodEnd;
    }

    /**
     * Every distinct real quarter this instrument has, ascending, canonicalized the same way as
     * {@link #findHistory} (the latest {@code as_of_date} per {@code latest_period_end}) - used by
     * {@code OwnershipTransformationSequenceOrchestrator.backfill()} to replay a point-in-time
     * evaluation once per real quarter, never once per raw availability-recompute row.
     */
    List<LocalDate> findAllAsOfDates(UUID instrumentId) {
        return jdbcTemplate.query(
            """
            SELECT MAX(as_of_date) AS as_of_date
            FROM ownership.transformation_states
            WHERE instrument_id = ?
            GROUP BY latest_period_end
            ORDER BY latest_period_end ASC
            """,
            (rs, rowNum) -> rs.getDate("as_of_date").toLocalDate(),
            instrumentId
        );
    }

    private record CanonicalStateRow(UUID id, String symbol, LocalDate asOfDate, LocalDate latestPeriodEnd, TransformationMetric drivingMetric) {
    }
}
