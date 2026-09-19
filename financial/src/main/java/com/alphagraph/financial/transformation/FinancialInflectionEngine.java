package com.alphagraph.financial.transformation;

import com.alphagraph.common.inflection.VelocityBand;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure calculation - no JDBC, no I/O - same convention as
 * {@code ownership.transformation.OwnershipTransformationEngine}.
 *
 * <p><b>Acceleration is computed on an independently-derived, always-sequential growth-rate
 * series - never Stage 1's own stored {@code change}/{@code comparatorUsed}.</b> A single evidence
 * row's {@code comparatorUsed} can be {@code YOY} or {@code QOQ_ONLY} depending on whether a real
 * 12-months-back point existed in the 5-quarter window when it was computed (confirmed live: only
 * the newest transition ever gets {@code YOY}, every older one is {@code QOQ_ONLY}) - comparing
 * those `change` values against each other across rows would be comparing different things.
 * Instead, {@link #computeAcceleration} rebuilds a raw-value series directly from each row's own
 * {@code value()} (plus the oldest row's {@code priorValue()} as one extra earliest point), always
 * consecutive real quarters by construction, and derives its own single-basis growth-rate series
 * from that.
 *
 * <p><b>{@code OPERATING_MARGIN} is an operating-profit/EBIT-like margin, not EBITDA</b> - verified
 * against {@code FinancialResultsComparisionNormalizer}'s real formula
 * (profit-before-tax + interest - other income, over revenue; depreciation is never added back).
 * Every derived quantity here is called "operating profit," never "EBITDA".
 *
 * <p><b>Point-in-time caveat</b>: every {@code asOfDate} here is a quarter-end {@code periodEnd()},
 * not the date the result was actually published/known to investors. These rows are not
 * point-in-time-safe for historical backtesting until a real {@code result_publication_date}/
 * {@code source_published_at} field exists upstream - Stage 3/4 must not treat {@code as_of_date}
 * as "date known" without that, or historical reconstruction will carry look-ahead bias.
 */
@Component
class FinancialInflectionEngine {

    private static final int ACCELERATION_PERSISTENCE_CAP = 3;
    private static final int MARGIN_PERSISTENCE_THRESHOLD = 3;
    private static final double BASE_CONFIDENCE = 75.0;
    private static final double THINNESS_PENALTY = 10.0;

    FinancialInflectionResult calculate(
        UUID instrumentId, String symbol,
        List<FinancialEvidenceObservation> revenueRowsAscending, List<FinancialEvidenceObservation> patRowsAscending,
        FinancialEvidenceObservation latestMargin, FinancialEvidenceObservation latestInterestExpense
    ) {
        Optional<AccelerationResult> revenueAccel = computeAcceleration(revenueRowsAscending);
        Optional<AccelerationResult> patAccel = computeAcceleration(patRowsAscending);
        FinancialEvidenceObservation latestRevenue = lastOrNull(revenueRowsAscending);
        FinancialEvidenceObservation latestPat = lastOrNull(patRowsAscending);

        boolean revenueAccelerates = revenueAccel.map(AccelerationResult::fires).orElse(false);
        boolean patAccelerates = patAccel.map(AccelerationResult::fires).orElse(false);
        boolean marginExpanding = latestMargin != null && changePositive(latestMargin) && latestMargin.persistenceQuarters() >= MARGIN_PERSISTENCE_THRESHOLD;
        BigDecimal interestChangePct = interestChangePct(latestInterestExpense);
        boolean interestCostDeclining = interestChangePct != null && interestChangePct.signum() < 0
            && latestInterestExpense.persistenceQuarters() >= MARGIN_PERSISTENCE_THRESHOLD;

        List<ReasonCode> reasons = new ArrayList<>();
        if (revenueAccelerates) {
            reasons.add(ReasonCode.of("REVENUE_GROWTH_IMPROVING", revenueAccel.get().change().doubleValue()));
        }
        if (patAccelerates) {
            reasons.add(ReasonCode.of("PAT_GROWTH_IMPROVING", patAccel.get().change().doubleValue()));
        }
        if (marginExpanding) {
            reasons.add(ReasonCode.of("MARGIN_EXPANDING_SUSTAINED", latestMargin.change().doubleValue()));
        }
        if (interestCostDeclining) {
            reasons.add(ReasonCode.of("INTEREST_EXPENSE_FALLING", interestChangePct.doubleValue()));
        }

        OperatingLeverageResult leverage = computeOperatingLeverage(latestRevenue, latestMargin, latestPat);
        if (leverage.fires()) {
            reasons.add(ReasonCode.of("REVENUE_TO_OPERATING_PROFIT_LEVERAGE", leverage.operatingProfitGrowthPct().subtract(leverage.revenueGrowthPct()).doubleValue()));
            reasons.add(ReasonCode.of("OPERATING_PROFIT_TO_PAT_LEVERAGE", leverage.patGrowthPct().subtract(leverage.operatingProfitGrowthPct()).doubleValue()));
        }

        boolean convergenceFires = revenueAccelerates && patAccelerates && marginExpanding
            && periodsAlign(revenueAccel.get().periodEnd(), patAccel.get().periodEnd(), latestMargin.periodEnd());
        if (convergenceFires) {
            reasons.add(ReasonCode.of("COMBINED_GROWTH_MARGIN_PAT"));
        }

        FinancialInflectionState primaryState;
        if (convergenceFires) {
            primaryState = FinancialInflectionState.EARNINGS_INFLECTION_CONVERGENCE;
        } else if (leverage.fires()) {
            primaryState = FinancialInflectionState.OPERATING_LEVERAGE_INFLECTION;
        } else if (patAccelerates) {
            primaryState = FinancialInflectionState.PAT_ACCELERATION;
        } else if (marginExpanding) {
            primaryState = FinancialInflectionState.STRUCTURAL_MARGIN_EXPANSION;
        } else if (revenueAccelerates) {
            primaryState = FinancialInflectionState.REVENUE_ACCELERATION;
        } else if (interestCostDeclining) {
            primaryState = FinancialInflectionState.INTEREST_COST_DECLINING;
        } else {
            primaryState = FinancialInflectionState.NO_CLEAR_SIGNAL;
        }

        return buildResult(
            primaryState, instrumentId, symbol, revenueAccel, patAccel, latestRevenue, latestMargin, latestPat,
            latestInterestExpense, interestChangePct, leverage, reasons
        );
    }

    private FinancialInflectionResult buildResult(
        FinancialInflectionState primaryState, UUID instrumentId, String symbol,
        Optional<AccelerationResult> revenueAccel, Optional<AccelerationResult> patAccel, FinancialEvidenceObservation latestRevenue,
        FinancialEvidenceObservation latestMargin, FinancialEvidenceObservation latestPat,
        FinancialEvidenceObservation latestInterestExpense, BigDecimal interestChangePct,
        OperatingLeverageResult leverage, List<ReasonCode> reasons
    ) {
        FinancialMetric drivingMetric;
        BigDecimal level;
        BigDecimal change;
        VelocityBand velocityBand;
        int persistence;
        LocalDate asOfDate;
        boolean hasPrior;

        switch (primaryState) {
            // hasPrior is unconditionally true for both acceleration states, not a shortcut - an
            // AccelerationResult only ever has fires=true when computeAcceleration found >= 3 real
            // raw values (2 real growth-rate comparisons), so a firing acceleration state can never
            // be the "first observation, no prior" case the thinness penalty exists for.
            case REVENUE_ACCELERATION -> {
                drivingMetric = FinancialMetric.REVENUE;
                level = revenueAccel.get().level();
                change = revenueAccel.get().change();
                velocityBand = FinancialVelocityBanding.bandPercentagePoint(change);
                persistence = revenueAccel.get().persistence();
                asOfDate = revenueAccel.get().periodEnd();
                hasPrior = true;
            }
            case PAT_ACCELERATION -> {
                drivingMetric = FinancialMetric.PAT;
                level = patAccel.get().level();
                change = patAccel.get().change();
                velocityBand = FinancialVelocityBanding.bandPercentagePoint(change);
                persistence = patAccel.get().persistence();
                asOfDate = patAccel.get().periodEnd();
                hasPrior = true;
            }
            case STRUCTURAL_MARGIN_EXPANSION -> {
                drivingMetric = FinancialMetric.OPERATING_MARGIN;
                level = latestMargin.value();
                change = latestMargin.change();
                velocityBand = FinancialVelocityBanding.bandPercentagePoint(change);
                persistence = Math.min(ACCELERATION_PERSISTENCE_CAP, latestMargin.persistenceQuarters());
                asOfDate = latestMargin.periodEnd();
                hasPrior = latestMargin.priorPeriodEnd() != null;
            }
            case OPERATING_LEVERAGE_INFLECTION -> {
                drivingMetric = FinancialMetric.PAT;
                level = leverage.patGrowthPct();
                change = leverage.patGrowthPct().subtract(leverage.revenueGrowthPct());
                velocityBand = FinancialVelocityBanding.bandPercentagePoint(change);
                persistence = 0;
                asOfDate = latestPat.periodEnd();
                hasPrior = latestPat.priorPeriodEnd() != null;
            }
            case EARNINGS_INFLECTION_CONVERGENCE -> {
                drivingMetric = FinancialMetric.PAT;
                level = patAccel.get().level();
                change = patAccel.get().change();
                velocityBand = FinancialVelocityBanding.bandPercentagePoint(change);
                persistence = Math.min(Math.min(revenueAccel.get().persistence(), patAccel.get().persistence()), Math.min(ACCELERATION_PERSISTENCE_CAP, latestMargin.persistenceQuarters()));
                asOfDate = patAccel.get().periodEnd();
                hasPrior = true;
            }
            case INTEREST_COST_DECLINING -> {
                drivingMetric = FinancialMetric.INTEREST_EXPENSE;
                level = latestInterestExpense.value();
                change = interestChangePct;
                velocityBand = FinancialVelocityBanding.bandDecliningCurrencyPct(interestChangePct);
                persistence = Math.min(ACCELERATION_PERSISTENCE_CAP, latestInterestExpense.persistenceQuarters());
                asOfDate = latestInterestExpense.periodEnd();
                hasPrior = latestInterestExpense.priorPeriodEnd() != null;
            }
            default -> {
                drivingMetric = null;
                level = null;
                change = null;
                velocityBand = null;
                persistence = 0;
                asOfDate = maxPeriodEnd(revenueAccel, patAccel, latestMargin, latestInterestExpense);
                hasPrior = true;
            }
        }

        double confidence = drivingMetric == null
            ? averageConfidence(latestRevenue, latestPat, latestMargin, latestInterestExpense)
            : clamp(BASE_CONFIDENCE + Math.min(10.0, 2.0 * persistence) - (hasPrior ? 0.0 : THINNESS_PENALTY), 0.0, 100.0);

        return new FinancialInflectionResult(
            instrumentId, symbol, asOfDate, primaryState, confidence,
            drivingMetric, level, change, velocityBand,
            persistence, reasons
        );
    }

    /** {@code null} when there's no prior value to compare against (first-ever observation) or it's zero (can't derive a percentage). */
    private static BigDecimal interestChangePct(FinancialEvidenceObservation latestInterestExpense) {
        if (latestInterestExpense == null || latestInterestExpense.value() == null || latestInterestExpense.priorValue() == null
            || latestInterestExpense.priorValue().signum() == 0) {
            return null;
        }
        return latestInterestExpense.value().subtract(latestInterestExpense.priorValue())
            .divide(latestInterestExpense.priorValue().abs(), 8, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
    }

    /**
     * Rebuilds a raw-value series directly from {@code value()} (never {@code change}/{@code
     * comparatorUsed}) so every growth-rate point in the resulting series is on the same,
     * always-sequential basis - see this class's own javadoc.
     */
    private static Optional<AccelerationResult> computeAcceleration(List<FinancialEvidenceObservation> rowsAscending) {
        if (rowsAscending.isEmpty()) {
            return Optional.empty();
        }
        LocalDate periodEnd = rowsAscending.get(rowsAscending.size() - 1).periodEnd();

        List<BigDecimal> values = new ArrayList<>();
        FinancialEvidenceObservation oldest = rowsAscending.get(0);
        if (oldest.priorValue() != null) {
            values.add(oldest.priorValue());
        }
        for (FinancialEvidenceObservation row : rowsAscending) {
            if (row.value() != null) {
                values.add(row.value());
            }
        }
        if (values.size() < 3) {
            return Optional.of(new AccelerationResult(false, null, null, 0, periodEnd));
        }

        List<BigDecimal> growthRates = new ArrayList<>();
        for (int i = 1; i < values.size(); i++) {
            BigDecimal prior = values.get(i - 1);
            if (prior.signum() == 0) {
                continue;
            }
            growthRates.add(values.get(i).subtract(prior).divide(prior, 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)));
        }
        if (growthRates.size() < 2) {
            return Optional.of(new AccelerationResult(false, null, null, 0, periodEnd));
        }

        int latestSign = growthRates.get(growthRates.size() - 1).subtract(growthRates.get(growthRates.size() - 2)).signum();
        int persistence = 0;
        if (latestSign > 0) {
            for (int i = growthRates.size() - 1; i > 0 && persistence < ACCELERATION_PERSISTENCE_CAP; i--) {
                if (growthRates.get(i).subtract(growthRates.get(i - 1)).signum() <= 0) {
                    break;
                }
                persistence++;
            }
        }

        BigDecimal level = growthRates.get(growthRates.size() - 1);
        BigDecimal change = level.subtract(growthRates.get(growthRates.size() - 2));
        return Optional.of(new AccelerationResult(persistence >= 1, level, change, persistence, periodEnd));
    }

    /** Not a multi-row walk - one real cross-metric comparison for the single most recent aligned quarter, so correction for mixed comparator bases doesn't apply here. */
    private static OperatingLeverageResult computeOperatingLeverage(
        FinancialEvidenceObservation revenue, FinancialEvidenceObservation margin, FinancialEvidenceObservation pat
    ) {
        if (revenue == null || margin == null || pat == null || !periodsAlign(revenue.periodEnd(), margin.periodEnd(), pat.periodEnd())) {
            return OperatingLeverageResult.NOT_FIRED;
        }

        BigDecimal revenueGrowthPct = growthPct(revenue.change(), revenue.priorValue());
        BigDecimal operatingProfitNow = operatingProfit(revenue.value(), margin.value());
        BigDecimal operatingProfitPrior = operatingProfit(revenue.priorValue(), margin.priorValue());
        BigDecimal operatingProfitGrowthPct = (operatingProfitNow == null || operatingProfitPrior == null)
            ? null : growthPct(operatingProfitNow.subtract(operatingProfitPrior), operatingProfitPrior);
        BigDecimal patGrowthPct = growthPct(pat.change(), pat.priorValue());

        if (revenueGrowthPct == null || operatingProfitGrowthPct == null || patGrowthPct == null) {
            return OperatingLeverageResult.NOT_FIRED;
        }
        boolean fires = revenueGrowthPct.signum() > 0
            && revenueGrowthPct.compareTo(operatingProfitGrowthPct) < 0
            && operatingProfitGrowthPct.compareTo(patGrowthPct) < 0;
        return new OperatingLeverageResult(fires, revenueGrowthPct, operatingProfitGrowthPct, patGrowthPct);
    }

    private static BigDecimal operatingProfit(BigDecimal revenue, BigDecimal marginPct) {
        if (revenue == null || marginPct == null) {
            return null;
        }
        return revenue.multiply(marginPct).divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
    }

    private static BigDecimal growthPct(BigDecimal change, BigDecimal priorValue) {
        if (change == null || priorValue == null || priorValue.signum() == 0) {
            return null;
        }
        return change.divide(priorValue, 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
    }

    private static boolean changePositive(FinancialEvidenceObservation observation) {
        return observation.change() != null && observation.change().signum() > 0;
    }

    private static boolean periodsAlign(LocalDate a, LocalDate b, LocalDate c) {
        return a != null && a.equals(b) && b.equals(c);
    }

    private static FinancialEvidenceObservation lastOrNull(List<FinancialEvidenceObservation> rowsAscending) {
        return rowsAscending.isEmpty() ? null : rowsAscending.get(rowsAscending.size() - 1);
    }

    private static LocalDate maxPeriodEnd(
        Optional<AccelerationResult> revenueAccel, Optional<AccelerationResult> patAccel,
        FinancialEvidenceObservation latestMargin, FinancialEvidenceObservation latestInterestExpense
    ) {
        LocalDate max = null;
        if (revenueAccel.isPresent()) {
            max = revenueAccel.get().periodEnd();
        }
        if (patAccel.isPresent() && (max == null || patAccel.get().periodEnd().isAfter(max))) {
            max = patAccel.get().periodEnd();
        }
        if (latestMargin != null && (max == null || latestMargin.periodEnd().isAfter(max))) {
            max = latestMargin.periodEnd();
        }
        if (latestInterestExpense != null && (max == null || latestInterestExpense.periodEnd().isAfter(max))) {
            max = latestInterestExpense.periodEnd();
        }
        return max;
    }

    private static double averageConfidence(FinancialEvidenceObservation... observations) {
        double sum = 0;
        int count = 0;
        for (FinancialEvidenceObservation observation : observations) {
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

    private record AccelerationResult(boolean fires, BigDecimal level, BigDecimal change, int persistence, LocalDate periodEnd) {
    }

    private record OperatingLeverageResult(boolean fires, BigDecimal revenueGrowthPct, BigDecimal operatingProfitGrowthPct, BigDecimal patGrowthPct) {
        static final OperatingLeverageResult NOT_FIRED = new OperatingLeverageResult(false, null, null, null);
    }
}
