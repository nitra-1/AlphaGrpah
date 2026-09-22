package com.alphagraph.financial.transformation;

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
 * Reads {@code financial.inflection_states}/{@code _state_reasons} history for Stage 3, merged with
 * REVENUE/PAT/OPERATING_MARGIN/INTEREST_EXPENSE's own {@link FinancialEvidenceObservation} history.
 *
 * <p><b>No canonicalization needed</b> - unlike Ownership, Financial's {@code as_of_date} is
 * already {@code period_end} directly (never a separately-drifting availability date), and all 4
 * metrics share one {@code FinancialResultsPeriod} per quarter from the same normalized Stage 1
 * row (verified against {@code FinancialTransformationEngine.calculate()}: the same
 * {@code current.periodEnd()} Java variable is written as every metric's evidence
 * {@code period_end}), so two rows can never exist for the same real quarter.
 *
 * <p><b>Evidence join is exact-{@code period_end}-only, never a fallback</b> - a reason that fired
 * in a given quarter's state row is guaranteed real evidence at that exact {@code period_end} (all
 * 4 metrics come from the same filing), so a missing exact match resolves to {@code null} (handled
 * defensively downstream), never an older observation.
 */
@Component
class FinancialInflectionHistoryReader {

    private final JdbcTemplate jdbcTemplate;
    private final FinancialTransformationEvidenceReader evidenceReader;

    FinancialInflectionHistoryReader(JdbcTemplate jdbcTemplate, FinancialTransformationEvidenceReader evidenceReader) {
        this.jdbcTemplate = jdbcTemplate;
        this.evidenceReader = evidenceReader;
    }

    List<FinancialInflectionHistoryEntry> findHistory(UUID instrumentId, LocalDate upToInclusiveOrNull, int limit) {
        String sql = "SELECT id, symbol, as_of_date FROM financial.inflection_states WHERE instrument_id = ?"
            + (upToInclusiveOrNull == null ? "" : " AND as_of_date <= ?")
            + " ORDER BY as_of_date DESC LIMIT ?";
        List<Object> args = new ArrayList<>();
        args.add(instrumentId);
        if (upToInclusiveOrNull != null) {
            args.add(Date.valueOf(upToInclusiveOrNull));
        }
        args.add(limit);

        List<StateRow> descending = jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new StateRow((UUID) rs.getObject("id"), rs.getString("symbol"), rs.getDate("as_of_date").toLocalDate()),
            args.toArray()
        );
        if (descending.isEmpty()) {
            return List.of();
        }

        Map<UUID, Map<String, Double>> reasonValuesByStateId = new HashMap<>();
        for (StateRow state : descending) {
            reasonValuesByStateId.put(state.id(), new HashMap<>());
        }
        List<UUID> stateIds = descending.stream().map(StateRow::id).toList();
        String placeholders = String.join(",", Collections.nCopies(stateIds.size(), "?"));
        jdbcTemplate.query(
            "SELECT state_id, reason_code, metric_value FROM financial.inflection_state_reasons WHERE state_id IN (" + placeholders + ")",
            rs -> {
                UUID stateId = (UUID) rs.getObject("state_id");
                String reasonCode = rs.getString("reason_code");
                // wasNull() reflects the LAST column read - capture it immediately after
                // getDouble(), before any other column read reassigns it.
                double metricValue = rs.getDouble("metric_value");
                Double value = rs.wasNull() ? null : metricValue;
                reasonValuesByStateId.get(stateId).put(reasonCode, value);
            },
            stateIds.toArray()
        );

        LocalDate newestPeriodEnd = descending.get(0).asOfDate();
        int evidenceLimit = descending.size() + 10;
        Map<LocalDate, FinancialEvidenceObservation> revenueByPeriod = byPeriodEndExact(evidenceReader.findHistory(instrumentId, FinancialMetric.REVENUE, newestPeriodEnd, evidenceLimit));
        Map<LocalDate, FinancialEvidenceObservation> patByPeriod = byPeriodEndExact(evidenceReader.findHistory(instrumentId, FinancialMetric.PAT, newestPeriodEnd, evidenceLimit));
        Map<LocalDate, FinancialEvidenceObservation> marginByPeriod = byPeriodEndExact(evidenceReader.findHistory(instrumentId, FinancialMetric.OPERATING_MARGIN, newestPeriodEnd, evidenceLimit));
        Map<LocalDate, FinancialEvidenceObservation> interestByPeriod = byPeriodEndExact(evidenceReader.findHistory(instrumentId, FinancialMetric.INTEREST_EXPENSE, newestPeriodEnd, evidenceLimit));

        List<FinancialInflectionHistoryEntry> ascending = new ArrayList<>();
        for (int i = descending.size() - 1; i >= 0; i--) {
            StateRow state = descending.get(i);
            ascending.add(new FinancialInflectionHistoryEntry(
                state.asOfDate(), state.symbol(), reasonValuesByStateId.get(state.id()),
                revenueByPeriod.get(state.asOfDate()), patByPeriod.get(state.asOfDate()),
                marginByPeriod.get(state.asOfDate()), interestByPeriod.get(state.asOfDate())
            ));
        }
        return ascending;
    }

    private static Map<LocalDate, FinancialEvidenceObservation> byPeriodEndExact(List<FinancialEvidenceObservation> evidenceAscending) {
        Map<LocalDate, FinancialEvidenceObservation> byPeriodEnd = new HashMap<>();
        for (FinancialEvidenceObservation observation : evidenceAscending) {
            byPeriodEnd.put(observation.periodEnd(), observation);
        }
        return byPeriodEnd;
    }

    /** Every real {@code as_of_date} this instrument has, ascending - used by {@code FinancialTransformationSequenceOrchestrator.backfill()} (not currently exposed as a live job - see its own javadoc). */
    List<LocalDate> findAllAsOfDates(UUID instrumentId) {
        return jdbcTemplate.query(
            "SELECT as_of_date FROM financial.inflection_states WHERE instrument_id = ? ORDER BY as_of_date ASC",
            (rs, rowNum) -> rs.getDate("as_of_date").toLocalDate(),
            instrumentId
        );
    }

    private record StateRow(UUID id, String symbol, LocalDate asOfDate) {
    }
}
