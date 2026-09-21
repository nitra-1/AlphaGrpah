package com.alphagraph.intelligence.riskcontradiction;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pure calculation - no JDBC, no I/O - same convention as every other family's Stage 2 engine.
 *
 * <p><b>Every sub-condition is checked via a reason code, never another family's winning
 * {@code primary_state}</b> - a higher-priority composite can win a family's own ladder while a
 * referenced sub-condition (e.g. {@code REVENUE_GROWTH_IMPROVING}) is still genuinely true
 * underneath it (Financial's own {@code STRUCTURAL_MARGIN_EXPANSION} ranks above
 * {@code REVENUE_ACCELERATION}; Market's {@code STEALTH_ACCUMULATION_CANDIDATE}/etc. all
 * structurally require {@code DELIVERY_EXPANSION}'s condition). Every family already records a
 * reason code for a sub-condition regardless of which state wins - checking {@code primary_state}
 * instead would silently miss real, currently-true contradictions.
 *
 * <p><b>Missing evidence never satisfies a "does NOT have reason X" check.</b> Every negative
 * sub-condition below is guarded by requiring the backing {@link FamilyStateSignal}/
 * {@link RawMetricPoint} to be non-null first - an absent row is "can't tell," never "confirmed
 * absent."
 *
 * <p><b>Temporal alignment</b>: same-domain triggers ({@code GROWTH_QUALITY_CONTRADICTION},
 * {@code PRICE_WITHOUT_DELIVERY_CONFIRMATION}) require their two reads to share the exact same
 * evidence date, mirroring {@code financial.transformation.FinancialInflectionEngine}'s own
 * {@code periodsAlign(...)} guard. The one cross-domain trigger
 * ({@code CAPITAL_RAISE_WITH_WEAK_BUSINESS_INFLECTION}) uses an anchor-date approach - Financial is
 * read as of the capital-raise event's own date, never from after it, and must be within
 * {@link #MAX_FINANCIAL_STALENESS_DAYS} of that anchor to count.
 */
@Component
class RiskContradictionEngine {

    /** One real quarter plus a filing-lag buffer - hardcoded Java threshold, never a DB rule, same convention every velocity band already uses. */
    private static final long MAX_FINANCIAL_STALENESS_DAYS = 120;
    private static final BigDecimal STRONG_PRICE_THRESHOLD = new BigDecimal("2.0");

    RiskContradictionResult calculate(
        UUID instrumentId, String symbol,
        FamilyStateSignal ownershipSignal,
        FamilyStateSignal financialState, RawMetricPoint marginPoint,
        FamilyStateSignal marketSignal, RawMetricPoint pricePoint,
        FamilyStateSignal corporateSignal, FamilyStateSignal financialAsOfAnchor
    ) {
        boolean growthQuality = growthQualityFires(financialState, marginPoint);
        boolean ownershipContradiction = ownershipSignal != null && ownershipSignal.hasReason("OWNERSHIP_CONTRADICTION");
        boolean priceWithoutDelivery = priceWithoutDeliveryFires(marketSignal, pricePoint);
        boolean capitalRaiseWeak = capitalRaiseWeakFires(corporateSignal, financialAsOfAnchor);

        List<ReasonCode> reasons = new ArrayList<>();
        List<Double> firedConfidences = new ArrayList<>();
        List<LocalDate> firedDates = new ArrayList<>();

        if (growthQuality) {
            reasons.add(ReasonCode.of("REVENUE_UP_MARGIN_DOWN", marginPoint.change().doubleValue()));
            firedConfidences.add(Math.min(financialState.confidence(), marginPoint.confidence()));
            firedDates.add(financialState.asOfDate());
        }
        if (ownershipContradiction) {
            reasons.addAll(ownershipSignal.reasons());
            firedConfidences.add(ownershipSignal.confidence());
            firedDates.add(ownershipSignal.asOfDate());
        }
        if (priceWithoutDelivery) {
            reasons.add(ReasonCode.of("PRICE_UP_DELIVERY_FLAT_OR_DOWN", pricePoint.change().doubleValue()));
            firedConfidences.add(Math.min(marketSignal.confidence(), pricePoint.confidence()));
            firedDates.add(marketSignal.asOfDate());
        }
        if (capitalRaiseWeak) {
            reasons.add(ReasonCode.of("EQUITY_RAISE_NO_GROWTH_SIGNAL"));
            firedConfidences.add(Math.min(corporateSignal.confidence(), financialAsOfAnchor.confidence()));
            firedDates.add(corporateSignal.asOfDate());
        }

        int fireCount = firedConfidences.size();

        RiskContradictionState primaryState;
        double confidence;
        LocalDate asOfDate;

        if (fireCount >= 2) {
            primaryState = RiskContradictionState.MULTI_DOMAIN_CONTRADICTION;
            reasons.add(ReasonCode.of("MULTIPLE_CONTRADICTIONS"));
            confidence = firedConfidences.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
            asOfDate = firedDates.stream().max(LocalDate::compareTo).orElseThrow();
        } else if (fireCount == 1) {
            primaryState = growthQuality ? RiskContradictionState.GROWTH_QUALITY_CONTRADICTION
                : ownershipContradiction ? RiskContradictionState.OWNERSHIP_CONTRADICTION
                : priceWithoutDelivery ? RiskContradictionState.PRICE_WITHOUT_DELIVERY_CONFIRMATION
                : RiskContradictionState.CAPITAL_RAISE_WITH_WEAK_BUSINESS_INFLECTION;
            confidence = firedConfidences.get(0);
            asOfDate = firedDates.get(0);
        } else {
            primaryState = RiskContradictionState.NO_CLEAR_SIGNAL;
            confidence = averageConfidence(ownershipSignal, financialState, marginPoint, marketSignal, pricePoint, corporateSignal);
            asOfDate = maxAsOfDate(ownershipSignal, financialState, marginPoint, marketSignal, pricePoint, corporateSignal);
        }

        int evidenceCount = countNonNull(ownershipSignal, financialState, marginPoint, marketSignal, pricePoint, corporateSignal);
        int evidenceCoveragePct = Math.round(evidenceCount / 6.0f * 100);
        RiskContradictionReadiness readiness = evidenceCount == 6 ? RiskContradictionReadiness.READY
            : evidenceCount == 0 ? RiskContradictionReadiness.INSUFFICIENT_DATA : RiskContradictionReadiness.PARTIAL_DATA;

        return new RiskContradictionResult(instrumentId, symbol, asOfDate, primaryState, confidence, evidenceCoveragePct, readiness, reasons);
    }

    private static boolean growthQualityFires(FamilyStateSignal financialState, RawMetricPoint marginPoint) {
        return financialState != null && marginPoint != null
            && financialState.asOfDate().equals(marginPoint.asOfDate())
            && financialState.hasReason("REVENUE_GROWTH_IMPROVING")
            && !financialState.hasReason("MARGIN_EXPANDING_SUSTAINED")
            && marginPoint.change() != null && marginPoint.change().signum() < 0;
    }

    /** Requires genuine positive price strength (value > 0), not merely a large positive change that could still leave the 20-day return negative overall. */
    private static boolean priceWithoutDeliveryFires(FamilyStateSignal marketSignal, RawMetricPoint pricePoint) {
        return marketSignal != null && pricePoint != null
            && marketSignal.asOfDate().equals(pricePoint.asOfDate())
            && pricePoint.value() != null && pricePoint.value().signum() > 0
            && pricePoint.change() != null && pricePoint.change().compareTo(STRONG_PRICE_THRESHOLD) >= 0
            && !marketSignal.hasReason("DELIVERY_RISING");
    }

    /** {@code financialAsOfAnchor} is read as of corporateSignal's own date (never after it) - null here means either the equity-raise prerequisite never held (so the caller never attempted the read) or Financial genuinely has no data at or before the anchor. Either way, "neither fired" cannot be confirmed. */
    private static boolean capitalRaiseWeakFires(FamilyStateSignal corporateSignal, FamilyStateSignal financialAsOfAnchor) {
        if (corporateSignal == null || !corporateSignal.hasReason("EQUITY_RAISE_EVENT_COUNT_180D_NONZERO")) {
            return false;
        }
        if (financialAsOfAnchor == null) {
            return false;
        }
        long ageDays = ChronoUnit.DAYS.between(financialAsOfAnchor.asOfDate(), corporateSignal.asOfDate());
        if (ageDays > MAX_FINANCIAL_STALENESS_DAYS) {
            return false;
        }
        return !financialAsOfAnchor.hasReason("REVENUE_GROWTH_IMPROVING") && !financialAsOfAnchor.hasReason("PAT_GROWTH_IMPROVING");
    }

    private static int countNonNull(FamilyStateSignal ownershipSignal, FamilyStateSignal financialState, RawMetricPoint marginPoint,
                                     FamilyStateSignal marketSignal, RawMetricPoint pricePoint, FamilyStateSignal corporateSignal) {
        int count = 0;
        if (ownershipSignal != null) count++;
        if (financialState != null) count++;
        if (marginPoint != null) count++;
        if (marketSignal != null) count++;
        if (pricePoint != null) count++;
        if (corporateSignal != null) count++;
        return count;
    }

    private static double averageConfidence(FamilyStateSignal ownershipSignal, FamilyStateSignal financialState, RawMetricPoint marginPoint,
                                              FamilyStateSignal marketSignal, RawMetricPoint pricePoint, FamilyStateSignal corporateSignal) {
        double sum = 0;
        int count = 0;
        if (ownershipSignal != null) { sum += ownershipSignal.confidence(); count++; }
        if (financialState != null) { sum += financialState.confidence(); count++; }
        if (marginPoint != null) { sum += marginPoint.confidence(); count++; }
        if (marketSignal != null) { sum += marketSignal.confidence(); count++; }
        if (pricePoint != null) { sum += pricePoint.confidence(); count++; }
        if (corporateSignal != null) { sum += corporateSignal.confidence(); count++; }
        return count == 0 ? 0.0 : Math.round((sum / count) * 100.0) / 100.0;
    }

    private static LocalDate maxAsOfDate(FamilyStateSignal ownershipSignal, FamilyStateSignal financialState, RawMetricPoint marginPoint,
                                          FamilyStateSignal marketSignal, RawMetricPoint pricePoint, FamilyStateSignal corporateSignal) {
        LocalDate max = null;
        if (ownershipSignal != null) max = laterOf(max, ownershipSignal.asOfDate());
        if (financialState != null) max = laterOf(max, financialState.asOfDate());
        if (marginPoint != null) max = laterOf(max, marginPoint.asOfDate());
        if (marketSignal != null) max = laterOf(max, marketSignal.asOfDate());
        if (pricePoint != null) max = laterOf(max, pricePoint.asOfDate());
        if (corporateSignal != null) max = laterOf(max, corporateSignal.asOfDate());
        return max;
    }

    private static LocalDate laterOf(LocalDate current, LocalDate candidate) {
        return current == null || candidate.isAfter(current) ? candidate : current;
    }
}
