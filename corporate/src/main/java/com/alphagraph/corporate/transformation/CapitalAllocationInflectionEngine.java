package com.alphagraph.corporate.transformation;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pure calculation - no JDBC, no I/O - same convention as
 * {@code market.transformation.MarketInflectionEngine}. No velocity band is computed - {@code
 * level}/{@code change} are plain rolling-180-day event counts (Tier 1's own {@code
 * BUYBACK_EVENT_COUNT_180D}/{@code EQUITY_RAISE_EVENT_COUNT_180D} columns are whole numbers, per
 * §15.4's {@code COUNT} type, which has no thresholds to band against).
 *
 * <p>No thinness penalty ever applies here (unlike every other family's confidence formula) -
 * {@code CapitalAllocationEvidenceObservation} has no "first observation, null prior" case: a
 * trailing-180-day count is always a real number, even when zero, per
 * {@link CapitalAllocationEngine}'s own javadoc.
 */
@Component
class CapitalAllocationInflectionEngine {

    private static final double BASE_CONFIDENCE = 75.0;

    CapitalAllocationInflectionResult calculate(
        UUID instrumentId, String symbol, LocalDate asOfDate,
        CapitalAllocationEvidenceObservation buyback, CapitalAllocationEvidenceObservation equityRaise
    ) {
        int buybackCount = buyback == null ? 0 : buyback.value();
        int equityRaiseCount = equityRaise == null ? 0 : equityRaise.value();

        List<ReasonCode> reasons = new ArrayList<>();
        if (buybackCount > 0) {
            reasons.add(ReasonCode.of("BUYBACK_EVENT_COUNT_180D_NONZERO", buybackCount));
        }
        if (equityRaiseCount > 0) {
            reasons.add(ReasonCode.of("EQUITY_RAISE_EVENT_COUNT_180D_NONZERO", equityRaiseCount));
        }

        CapitalAllocationInflectionState primaryState;
        if (buybackCount > 0 && equityRaiseCount > 0) {
            primaryState = CapitalAllocationInflectionState.MIXED_CAPITAL_ALLOCATION_ACTIVITY;
        } else if (buybackCount > 0) {
            primaryState = CapitalAllocationInflectionState.BUYBACK_ACTIVITY;
        } else if (equityRaiseCount > 0) {
            primaryState = CapitalAllocationInflectionState.EQUITY_RAISE_ACTIVITY;
        } else {
            primaryState = CapitalAllocationInflectionState.NO_CLEAR_SIGNAL;
        }

        return buildResult(primaryState, instrumentId, symbol, asOfDate, buyback, equityRaise, reasons);
    }

    private CapitalAllocationInflectionResult buildResult(
        CapitalAllocationInflectionState primaryState, UUID instrumentId, String symbol, LocalDate asOfDate,
        CapitalAllocationEvidenceObservation buyback, CapitalAllocationEvidenceObservation equityRaise, List<ReasonCode> reasons
    ) {
        CapitalAllocationMetric drivingMetric;
        Integer level;
        Integer change;
        int persistence;

        switch (primaryState) {
            case BUYBACK_ACTIVITY -> {
                drivingMetric = CapitalAllocationMetric.BUYBACK_EVENT_COUNT_180D;
                level = buyback.value();
                change = buyback.change();
                persistence = buyback.persistenceDays();
            }
            case EQUITY_RAISE_ACTIVITY -> {
                drivingMetric = CapitalAllocationMetric.EQUITY_RAISE_EVENT_COUNT_180D;
                level = equityRaise.value();
                change = equityRaise.change();
                persistence = equityRaise.persistenceDays();
            }
            // Both genuinely participated (both counts > 0) - unlike Ownership's
            // OWNERSHIP_CONTRADICTION, there's no "picked the wrong side" risk here, so magnitude
            // is a fine tie-break for the representative fields; ties favor buyback, matching the
            // single-metric priority below.
            case MIXED_CAPITAL_ALLOCATION_ACTIVITY -> {
                boolean buybackLarger = buyback.value() >= equityRaise.value();
                drivingMetric = buybackLarger ? CapitalAllocationMetric.BUYBACK_EVENT_COUNT_180D : CapitalAllocationMetric.EQUITY_RAISE_EVENT_COUNT_180D;
                level = buybackLarger ? buyback.value() : equityRaise.value();
                change = buybackLarger ? buyback.change() : equityRaise.change();
                persistence = Math.min(buyback.persistenceDays(), equityRaise.persistenceDays());
            }
            default -> {
                drivingMetric = null;
                level = null;
                change = null;
                persistence = 0;
            }
        }

        double confidence = drivingMetric == null
            ? averageConfidence(buyback, equityRaise)
            : clamp(BASE_CONFIDENCE + Math.min(10.0, 2.0 * persistence), 0.0, 100.0);

        return new CapitalAllocationInflectionResult(instrumentId, symbol, asOfDate, primaryState, confidence, drivingMetric, level, change, persistence, reasons);
    }

    private static double averageConfidence(CapitalAllocationEvidenceObservation... observations) {
        double sum = 0;
        int count = 0;
        for (CapitalAllocationEvidenceObservation observation : observations) {
            if (observation != null) {
                sum += observation.confidence();
                count++;
            }
        }
        return count == 0 ? 0.0 : Math.round((sum / count) * 100.0) / 100.0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
