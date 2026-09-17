package com.alphagraph.financial.transformation;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure calculation - no JDBC, no I/O - same convention as
 * {@code ownership.transformation.OwnershipTransformationEngine}. Input is the live feed's own
 * (up to 5, ascending) quarters for one instrument - no DB round-trip to find "prior".
 *
 * <p>For each metric, prefers a real year-over-year comparison (the same calendar quarter roughly
 * 12 months back, if that point exists and has a non-null value for this metric) over a sequential
 * quarter-over-quarter one - Indian corporate results are seasonal enough that a bare QoQ
 * comparison can read as an inflection or decline that's really just the calendar.
 * {@code comparatorUsed} on the resulting observation discloses which was actually used.
 * {@code persistenceQuarters} is always the quarter-over-quarter sign-walk regardless of which
 * comparator produced the headline change - persistence answers "how many consecutive quarters has
 * this metric moved the same direction," an inherently sequential question separate from how far
 * back the magnitude comparison reached.
 */
@Component
class FinancialTransformationEngine {

    private static final double CONFIDENCE = 90.0;
    private static final double FIRST_OBSERVATION_CONFIDENCE = 40.0;
    private static final long YOY_MIN_MONTHS = 11;
    private static final long YOY_MAX_MONTHS = 13;

    List<FinancialEvidenceObservation> calculate(List<FinancialResultsPeriod> periodsAscending) {
        if (periodsAscending.isEmpty()) {
            return List.of();
        }
        FinancialResultsPeriod current = periodsAscending.get(periodsAscending.size() - 1);

        List<FinancialEvidenceObservation> evidence = new ArrayList<>();
        for (FinancialMetric metric : FinancialMetric.values()) {
            BigDecimal currentValue = current.valueFor(metric);
            if (currentValue == null) {
                continue;
            }
            evidence.add(computeObservation(metric, periodsAscending, current, currentValue));
        }
        return evidence;
    }

    private FinancialEvidenceObservation computeObservation(
        FinancialMetric metric, List<FinancialResultsPeriod> periodsAscending, FinancialResultsPeriod current, BigDecimal currentValue
    ) {
        Optional<FinancialResultsPeriod> yoyPrior = findYoyPrior(metric, periodsAscending, current);
        FinancialResultsPeriod priorPeriod = yoyPrior.orElseGet(() -> findQoqPrior(metric, periodsAscending));
        String comparatorUsed = yoyPrior.isPresent() ? "YOY" : (priorPeriod == null ? null : "QOQ_ONLY");

        if (priorPeriod == null) {
            return new FinancialEvidenceObservation(
                metric, current.instrumentId(), current.symbol(), current.periodEnd(), null,
                currentValue, null, null, null, 0, FIRST_OBSERVATION_CONFIDENCE, null
            );
        }

        BigDecimal priorValue = priorPeriod.valueFor(metric);
        BigDecimal change = currentValue.subtract(priorValue);
        double quartersElapsed = Math.max(1.0, quartersBetween(priorPeriod.periodEnd(), current.periodEnd()));
        BigDecimal velocity = change.divide(BigDecimal.valueOf(quartersElapsed), 4, RoundingMode.HALF_UP);
        int persistence = persistenceQuarters(metric, periodsAscending);

        return new FinancialEvidenceObservation(
            metric, current.instrumentId(), current.symbol(), current.periodEnd(), priorPeriod.periodEnd(),
            currentValue, priorValue, change, velocity, persistence, CONFIDENCE, comparatorUsed
        );
    }

    /** The period whose periodEnd lands 11-13 months before current's, with a non-null value for this metric - null if no such point exists in the window. */
    private static Optional<FinancialResultsPeriod> findYoyPrior(FinancialMetric metric, List<FinancialResultsPeriod> periodsAscending, FinancialResultsPeriod current) {
        for (int i = periodsAscending.size() - 2; i >= 0; i--) {
            FinancialResultsPeriod candidate = periodsAscending.get(i);
            long months = Period.between(candidate.periodEnd(), current.periodEnd()).toTotalMonths();
            if (months >= YOY_MIN_MONTHS && months <= YOY_MAX_MONTHS && candidate.valueFor(metric) != null) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static FinancialResultsPeriod findQoqPrior(FinancialMetric metric, List<FinancialResultsPeriod> periodsAscending) {
        for (int i = periodsAscending.size() - 2; i >= 0; i--) {
            FinancialResultsPeriod candidate = periodsAscending.get(i);
            if (candidate.valueFor(metric) != null) {
                return candidate;
            }
        }
        return null;
    }

    private static double quartersBetween(LocalDate from, LocalDate to) {
        return Period.between(from, to).toTotalMonths() / 3.0;
    }

    /** Counts consecutive same-sign quarter-over-quarter transitions between non-null observations, walking backward from the current one, including it. */
    private static int persistenceQuarters(FinancialMetric metric, List<FinancialResultsPeriod> periodsAscending) {
        List<BigDecimal> nonNullAscending = periodsAscending.stream().map(p -> p.valueFor(metric)).filter(Objects::nonNull).toList();
        if (nonNullAscending.size() < 2) {
            return 0;
        }
        int currentSign = nonNullAscending.get(nonNullAscending.size() - 1).subtract(nonNullAscending.get(nonNullAscending.size() - 2)).signum();
        if (currentSign == 0) {
            return 0;
        }
        int count = 0;
        for (int i = nonNullAscending.size() - 1; i > 0; i--) {
            int sign = nonNullAscending.get(i).subtract(nonNullAscending.get(i - 1)).signum();
            if (sign != currentSign) {
                break;
            }
            count++;
        }
        return count;
    }
}
