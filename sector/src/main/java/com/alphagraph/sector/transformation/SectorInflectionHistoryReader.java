package com.alphagraph.sector.transformation;

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
 * Reads {@code sector.inflection_states}/{@code _state_reasons} history for Stage 3, merged with
 * each of the 3 metrics' own {@link SectorEvidenceObservation} history via an as-of merge.
 *
 * <p><b>As-of merge, never exact-date match</b> - unlike Financial (where every metric shares one
 * unified source row's {@code period_end}), Sector reads 3 genuinely independently-dated metrics
 * per run; {@code sector.inflection_states.as_of_date} is stamped as whichever metric's own real
 * date drove that row's winning state, never a canonical "same real day across all 3" value.
 * {@link #asOfEachStateDate} (copied from {@code market.transformation.MarketInflectionHistoryReader})
 * resolves each metric's latest real evidence with {@code asOfDate <= } the state row's own date -
 * the right precedent here, since it's the same underlying shape as Market's Clock-vs-{@code
 * trade_date} lag (independently-dated evidence merged against a state date that doesn't always
 * match exactly), even though the root cause differs.
 *
 * <p><b>A real, disclosed, currently-dormant risk</b>: {@code sector.inflection_states} itself is
 * upserted ({@code ON CONFLICT ... DO UPDATE}), not append-only. Combined with {@code as_of_date}
 * being per-driving-metric rather than {@code Clock}-based, once {@code reference.sector_benchmarks}
 * gets populated, a stale-but-still-qualifying {@code VS_SECTOR} value could someday win the
 * priority ladder on a later run and silently overwrite an <i>older</i> {@code (instrument_id,
 * as_of_date)} slot a genuinely earlier run had already written correctly - a different, arguably
 * worse failure shape than Market's {@code Clock}-based design (which can only ever advance
 * forward). This cannot manifest today ({@code VS_NIFTY}/{@code VS_SECTOR} are simply empty, not
 * stale-with-old-dates) - flagged as a real Stage-2-level follow-up once {@code sector_benchmarks}
 * is populated, out of scope for this reader.
 */
@Component
class SectorInflectionHistoryReader {

    private final JdbcTemplate jdbcTemplate;
    private final SectorContextEvidenceReader evidenceReader;

    SectorInflectionHistoryReader(JdbcTemplate jdbcTemplate, SectorContextEvidenceReader evidenceReader) {
        this.jdbcTemplate = jdbcTemplate;
        this.evidenceReader = evidenceReader;
    }

    List<SectorInflectionHistoryEntry> findHistory(UUID instrumentId, LocalDate upToInclusiveOrNull, int limit) {
        String sql = "SELECT id, symbol, as_of_date FROM sector.inflection_states WHERE instrument_id = ?"
            + (upToInclusiveOrNull == null ? "" : " AND as_of_date <= ?")
            + " ORDER BY as_of_date DESC LIMIT ?";
        List<Object> args = new ArrayList<>();
        args.add(instrumentId);
        if (upToInclusiveOrNull != null) {
            args.add(Date.valueOf(upToInclusiveOrNull));
        }
        args.add(limit);

        List<StateRow> descendingStates = jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new StateRow((UUID) rs.getObject("id"), rs.getString("symbol"), rs.getDate("as_of_date").toLocalDate()),
            args.toArray()
        );
        if (descendingStates.isEmpty()) {
            return List.of();
        }

        Map<UUID, Set<String>> reasonsByStateId = new HashMap<>();
        for (StateRow state : descendingStates) {
            reasonsByStateId.put(state.id(), new HashSet<>());
        }
        List<UUID> stateIds = descendingStates.stream().map(StateRow::id).toList();
        String placeholders = String.join(",", Collections.nCopies(stateIds.size(), "?"));
        jdbcTemplate.query(
            "SELECT state_id, reason_code FROM sector.inflection_state_reasons WHERE state_id IN (" + placeholders + ")",
            rs -> {
                UUID stateId = (UUID) rs.getObject("state_id");
                reasonsByStateId.get(stateId).add(rs.getString("reason_code"));
            },
            stateIds.toArray()
        );

        LocalDate newestDate = descendingStates.get(0).asOfDate();
        List<LocalDate> stateDatesAscending = new ArrayList<>();
        for (int i = descendingStates.size() - 1; i >= 0; i--) {
            stateDatesAscending.add(descendingStates.get(i).asOfDate());
        }
        int evidenceLimit = stateDatesAscending.size() + 30;
        Map<LocalDate, SectorEvidenceObservation> sectorRsByDate = asOfEachStateDate(
            evidenceReader.findHistory(instrumentId, SectorMetric.SECTOR_RELATIVE_STRENGTH, newestDate, evidenceLimit), stateDatesAscending);
        Map<LocalDate, SectorEvidenceObservation> vsNiftyByDate = asOfEachStateDate(
            evidenceReader.findHistory(instrumentId, SectorMetric.INSTRUMENT_RELATIVE_STRENGTH_VS_NIFTY, newestDate, evidenceLimit), stateDatesAscending);
        Map<LocalDate, SectorEvidenceObservation> vsSectorByDate = asOfEachStateDate(
            evidenceReader.findHistory(instrumentId, SectorMetric.INSTRUMENT_RELATIVE_STRENGTH_VS_SECTOR, newestDate, evidenceLimit), stateDatesAscending);

        List<SectorInflectionHistoryEntry> ascending = new ArrayList<>();
        for (int i = descendingStates.size() - 1; i >= 0; i--) {
            StateRow state = descendingStates.get(i);
            ascending.add(new SectorInflectionHistoryEntry(
                state.asOfDate(), state.symbol(), reasonsByStateId.get(state.id()),
                sectorRsByDate.get(state.asOfDate()), vsNiftyByDate.get(state.asOfDate()), vsSectorByDate.get(state.asOfDate())
            ));
        }
        return ascending;
    }

    /**
     * For each state date, the latest evidence observation with {@code asOfDate <= } that date - an
     * as-of resolution, never an exact-date match. Both lists are ascending; a single forward
     * pointer over the evidence list is enough.
     */
    private static Map<LocalDate, SectorEvidenceObservation> asOfEachStateDate(List<SectorEvidenceObservation> evidenceAscending, List<LocalDate> stateDatesAscending) {
        Map<LocalDate, SectorEvidenceObservation> result = new HashMap<>();
        int evidenceIndex = 0;
        SectorEvidenceObservation latestSoFar = null;
        for (LocalDate stateDate : stateDatesAscending) {
            while (evidenceIndex < evidenceAscending.size() && !evidenceAscending.get(evidenceIndex).asOfDate().isAfter(stateDate)) {
                latestSoFar = evidenceAscending.get(evidenceIndex);
                evidenceIndex++;
            }
            result.put(stateDate, latestSoFar);
        }
        return result;
    }

    /** Every real {@code as_of_date} this instrument has, ascending - used by {@code SectorTransformationSequenceOrchestrator.backfill()}. */
    List<LocalDate> findAllAsOfDates(UUID instrumentId) {
        return jdbcTemplate.query(
            "SELECT as_of_date FROM sector.inflection_states WHERE instrument_id = ? ORDER BY as_of_date ASC",
            (rs, rowNum) -> rs.getDate("as_of_date").toLocalDate(),
            instrumentId
        );
    }

    private record StateRow(UUID id, String symbol, LocalDate asOfDate) {
    }
}
