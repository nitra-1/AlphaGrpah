package com.alphagraph.market.transformation;

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
 * Reads {@code market.inflection_states}/{@code _state_reasons} history (Stage 2's own writer was
 * only ever read latest-row-at-a-time before Stage 3 needed a real ascending window) and merges it,
 * date-by-date, with each of the 3 metrics' own {@link MarketEvidenceObservation} history from
 * {@link MarketTransformationEvidenceReader#findHistory}. Same point-in-time discipline as that
 * reader: {@code ORDER BY as_of_date DESC LIMIT ?} first, reversed to ascending in Java - never the
 * oldest {@code limit} rows truncated.
 */
@Component
class MarketInflectionHistoryReader {

    private final JdbcTemplate jdbcTemplate;
    private final MarketTransformationEvidenceReader evidenceReader;

    MarketInflectionHistoryReader(JdbcTemplate jdbcTemplate, MarketTransformationEvidenceReader evidenceReader) {
        this.jdbcTemplate = jdbcTemplate;
        this.evidenceReader = evidenceReader;
    }

    List<MarketInflectionHistoryEntry> findHistory(UUID instrumentId, LocalDate upToInclusiveOrNull, int limit) {
        List<StateRow> descendingStates = jdbcTemplate.query(
            "SELECT id, symbol, as_of_date FROM market.inflection_states WHERE instrument_id = ?"
                + (upToInclusiveOrNull == null ? "" : " AND as_of_date <= ?")
                + " ORDER BY as_of_date DESC LIMIT ?",
            (rs, rowNum) -> new StateRow((UUID) rs.getObject("id"), rs.getString("symbol"), rs.getDate("as_of_date").toLocalDate()),
            upToInclusiveOrNull == null ? new Object[] {instrumentId, limit} : new Object[] {instrumentId, Date.valueOf(upToInclusiveOrNull), limit}
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
            "SELECT state_id, reason_code FROM market.inflection_state_reasons WHERE state_id IN (" + placeholders + ")",
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
        // Generous buffer, not a 1:1 assumption - market.inflection_states' own as_of_date is
        // Clock-based (real Market Stage 2 design, confirmed live), so an individual metric's own
        // latest real evidence can genuinely lag a day or more behind the state row's date; each
        // metric needs enough of its own real history fetched to resolve "as of" correctly below.
        int evidenceLimit = stateDatesAscending.size() + 30;
        Map<LocalDate, MarketEvidenceObservation> deliveryByDate = asOfEachStateDate(evidenceReader.findHistory(instrumentId, MarketMetric.DELIVERY_PERCENTAGE_20D_AVG, newestDate, evidenceLimit), stateDatesAscending);
        Map<LocalDate, MarketEvidenceObservation> relativeVolumeByDate = asOfEachStateDate(evidenceReader.findHistory(instrumentId, MarketMetric.RELATIVE_VOLUME, newestDate, evidenceLimit), stateDatesAscending);
        Map<LocalDate, MarketEvidenceObservation> priceReturnByDate = asOfEachStateDate(evidenceReader.findHistory(instrumentId, MarketMetric.PRICE_RETURN_20D, newestDate, evidenceLimit), stateDatesAscending);

        List<MarketInflectionHistoryEntry> ascending = new ArrayList<>();
        for (int i = descendingStates.size() - 1; i >= 0; i--) {
            StateRow state = descendingStates.get(i);
            ascending.add(new MarketInflectionHistoryEntry(
                state.asOfDate(), state.symbol(), reasonsByStateId.get(state.id()),
                deliveryByDate.get(state.asOfDate()), relativeVolumeByDate.get(state.asOfDate()), priceReturnByDate.get(state.asOfDate())
            ));
        }
        return ascending;
    }

    /**
     * For each state date, the latest evidence observation with {@code tradeDate <= } that date -
     * an as-of resolution, never an exact-date match. {@code market.inflection_states.as_of_date}
     * is Clock-based, so it will not always exactly equal every individual metric's own latest real
     * {@code trade_date} (confirmed live: a metric can genuinely lag a day behind the state row it
     * contributed to) - an exact match would silently drop real evidence that legitimately applies.
     * Both lists are ascending; a single forward pointer over the evidence list is enough.
     */
    private static Map<LocalDate, MarketEvidenceObservation> asOfEachStateDate(List<MarketEvidenceObservation> evidenceAscending, List<LocalDate> stateDatesAscending) {
        Map<LocalDate, MarketEvidenceObservation> result = new HashMap<>();
        int evidenceIndex = 0;
        MarketEvidenceObservation latestSoFar = null;
        for (LocalDate stateDate : stateDatesAscending) {
            while (evidenceIndex < evidenceAscending.size() && !evidenceAscending.get(evidenceIndex).tradeDate().isAfter(stateDate)) {
                latestSoFar = evidenceAscending.get(evidenceIndex);
                evidenceIndex++;
            }
            result.put(stateDate, latestSoFar);
        }
        return result;
    }

    /** Every real {@code as_of_date} this instrument has, ascending - used by {@code MarketTransformationSequenceOrchestrator.backfill()} to replay a point-in-time evaluation for each real historical day. */
    List<LocalDate> findAllAsOfDates(UUID instrumentId) {
        return jdbcTemplate.query(
            "SELECT as_of_date FROM market.inflection_states WHERE instrument_id = ? ORDER BY as_of_date ASC",
            (rs, rowNum) -> rs.getDate("as_of_date").toLocalDate(),
            instrumentId
        );
    }

    private record StateRow(UUID id, String symbol, LocalDate asOfDate) {
    }
}
