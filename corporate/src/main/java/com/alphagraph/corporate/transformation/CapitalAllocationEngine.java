package com.alphagraph.corporate.transformation;

import com.alphagraph.corporate.api.CorporateAction;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Pure calculation - no JDBC, no I/O - same convention as
 * {@code ownership.transformation.OwnershipTransformationEngine}. Unlike the other Stage 1
 * engines, the underlying quantity (a rolling 180-day count of real events) is always fully
 * computable - there is no "not enough history yet" case, so every observation has a real prior
 * value, even when that value is zero.
 *
 * <p>Only computes a metric for an instrument once that action type has occurred for it at least
 * once, ever - an instrument that has simply never done a buyback gets no
 * {@code BUYBACK_EVENT_COUNT_180D} evidence at all, rather than a permanent zero row written every
 * single day forever with no new information in it.
 */
@Component
class CapitalAllocationEngine {

    static final int WINDOW_DAYS = 180;
    private static final int PERSISTENCE_LOOKBACK_DAYS = 400;
    private static final double CONFIDENCE = 90.0;

    List<CapitalAllocationEvidenceObservation> calculate(UUID instrumentId, String symbol, List<CorporateAction> actionsAscending, LocalDate asOfDate) {
        List<CapitalAllocationEvidenceObservation> evidence = new ArrayList<>();
        for (CapitalAllocationMetric metric : CapitalAllocationMetric.values()) {
            List<LocalDate> exDates = exDatesFor(metric, actionsAscending);
            if (exDates.isEmpty()) {
                continue;
            }
            evidence.add(computeObservation(metric, instrumentId, symbol, exDates, asOfDate));
        }
        return evidence;
    }

    private static CapitalAllocationEvidenceObservation computeObservation(
        CapitalAllocationMetric metric, UUID instrumentId, String symbol, List<LocalDate> exDates, LocalDate asOfDate
    ) {
        LocalDate priorAsOfDate = asOfDate.minusDays(1);
        int currentValue = countInWindow(exDates, asOfDate);
        int priorValue = countInWindow(exDates, priorAsOfDate);
        int change = currentValue - priorValue;
        int persistence = persistenceDays(exDates, asOfDate);

        return new CapitalAllocationEvidenceObservation(
            metric, instrumentId, symbol, asOfDate, priorAsOfDate, currentValue, priorValue, change, change, persistence, CONFIDENCE, WINDOW_DAYS
        );
    }

    private static int countInWindow(List<LocalDate> exDatesAscending, LocalDate asOfDate) {
        LocalDate windowStartExclusive = asOfDate.minusDays(WINDOW_DAYS);
        return (int) exDatesAscending.stream().filter(d -> d.isAfter(windowStartExclusive) && !d.isAfter(asOfDate)).count();
    }

    /** Consecutive prior days (walking backward from the day before asOfDate) with a non-zero rolling count, bounded so a permanent streak can't loop forever. */
    private static int persistenceDays(List<LocalDate> exDatesAscending, LocalDate asOfDate) {
        int count = 0;
        for (int i = 1; i <= PERSISTENCE_LOOKBACK_DAYS; i++) {
            LocalDate day = asOfDate.minusDays(i);
            if (countInWindow(exDatesAscending, day) == 0) {
                break;
            }
            count++;
        }
        return count;
    }

    private static List<LocalDate> exDatesFor(CapitalAllocationMetric metric, List<CorporateAction> actionsAscending) {
        Set<String> actionTypes = actionTypesFor(metric);
        return actionsAscending.stream()
            .filter(a -> actionTypes.contains(a.actionType()))
            .map(CorporateAction::exDate)
            .toList();
    }

    private static Set<String> actionTypesFor(CapitalAllocationMetric metric) {
        return switch (metric) {
            case BUYBACK_EVENT_COUNT_180D -> Set.of("BUYBACK");
            case EQUITY_RAISE_EVENT_COUNT_180D -> Set.of("RIGHTS");
        };
    }
}
