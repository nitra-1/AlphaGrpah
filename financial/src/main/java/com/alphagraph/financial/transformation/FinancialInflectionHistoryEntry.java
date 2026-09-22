package com.alphagraph.financial.transformation;

import java.time.LocalDate;
import java.util.Map;

/**
 * One real Financial Stage 2 row's full reason-code set from {@code financial.inflection_states}/
 * {@code _state_reasons}, plus REVENUE/PAT/OPERATING_MARGIN/INTEREST_EXPENSE's own
 * {@link FinancialEvidenceObservation} at the exact same {@code period_end = asOfDate} (may be
 * {@code null} per metric if that metric had no real evidence that specific quarter).
 *
 * <p>{@code reasonValues} is a {@code Map<String, Double>}, not a bare reason-code set like
 * Market's/Ownership's own history entries - {@code BUSINESS_ACCELERATION_CYCLE}'s velocity gate
 * needs the real {@code metric_value} Stage 2 already persisted for {@code REVENUE_GROWTH_IMPROVING}
 * (the acceleration delta itself, which has no Stage 1 equivalent - see
 * {@code FinancialTransformationSequenceEngine}'s javadoc), not just presence/absence.
 */
record FinancialInflectionHistoryEntry(
    LocalDate asOfDate, String symbol, Map<String, Double> reasonValues,
    FinancialEvidenceObservation revenue, FinancialEvidenceObservation pat,
    FinancialEvidenceObservation margin, FinancialEvidenceObservation interest
) {

    boolean hasReason(String code) {
        return reasonValues.containsKey(code);
    }

    Double metricValueFor(String code) {
        return reasonValues.get(code);
    }
}
