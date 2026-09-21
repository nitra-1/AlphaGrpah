package com.alphagraph.intelligence.riskcontradiction;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RiskContradictionEngineTest {

    private static final UUID INSTRUMENT_ID = UUID.randomUUID();
    private static final String SYMBOL = "RELIANCE";
    private static final LocalDate D1 = LocalDate.of(2026, 9, 18);
    private static final LocalDate D2 = LocalDate.of(2026, 9, 17);

    private final RiskContradictionEngine engine = new RiskContradictionEngine();

    @Test
    void growthQualityContradictionFiresWhenMarginNotExpandingAndOperatingMarginDeclines() {
        var financialState = signal(D1, 80.0, "REVENUE_GROWTH_IMPROVING");
        var marginPoint = point(D1, null, "-1.50", 70.0);

        var result = calculate(null, financialState, marginPoint, null, null, null, null);

        assertThat(result.primaryState()).isEqualTo(RiskContradictionState.GROWTH_QUALITY_CONTRADICTION);
        assertThat(result.confidence()).isEqualTo(70.0); // min(80, 70)
        assertThat(result.asOfDate()).isEqualTo(D1);
        assertThat(result.reasons()).extracting("code").containsExactly("REVENUE_UP_MARGIN_DOWN");
    }

    @Test
    void growthQualityDoesNotFireWhenMarginExpandingSustainedReasonIsAlsoPresent() {
        var financialState = signal(D1, 80.0, "REVENUE_GROWTH_IMPROVING", "MARGIN_EXPANDING_SUSTAINED");
        var marginPoint = point(D1, null, "-1.50", 70.0);

        var result = calculate(null, financialState, marginPoint, null, null, null, null);

        assertThat(result.primaryState()).isEqualTo(RiskContradictionState.NO_CLEAR_SIGNAL);
    }

    @Test
    void growthQualityDoesNotFireWhenTheTwoFinancialReadsAreFromDifferentReportingPeriods() {
        var financialState = signal(D1, 80.0, "REVENUE_GROWTH_IMPROVING");
        var marginPoint = point(D2, null, "-1.50", 70.0); // different as_of_date

        var result = calculate(null, financialState, marginPoint, null, null, null, null);

        assertThat(result.primaryState()).isEqualTo(RiskContradictionState.NO_CLEAR_SIGNAL);
    }

    @Test
    void ownershipContradictionFiresAndReusesOwnershipsOwnConfidenceDirectly() {
        var ownershipSignal = signal(D1, 81.0, "OWNERSHIP_CONTRADICTION");

        var result = calculate(ownershipSignal, null, null, null, null, null, null);

        assertThat(result.primaryState()).isEqualTo(RiskContradictionState.OWNERSHIP_CONTRADICTION);
        assertThat(result.confidence()).isEqualTo(81.0);
        assertThat(result.reasons()).extracting("code").containsExactly("OWNERSHIP_CONTRADICTION");
    }

    @Test
    void priceWithoutDeliveryConfirmationFiresOnGenuinePositiveStrengthWithNoDeliveryReason() {
        var marketSignal = signal(D1, 90.0);
        var pricePoint = point(D1, "1.2", "3.5", 88.0);

        var result = calculate(null, null, null, marketSignal, pricePoint, null, null);

        assertThat(result.primaryState()).isEqualTo(RiskContradictionState.PRICE_WITHOUT_DELIVERY_CONFIRMATION);
        assertThat(result.confidence()).isEqualTo(88.0); // min(90, 88)
    }

    @Test
    void priceWithoutDeliveryDoesNotFireWhenValueIsStillNegativeDespiteALargePositiveChange() {
        var marketSignal = signal(D1, 90.0);
        var pricePoint = point(D1, "-12.0", "3.0", 88.0); // -15% -> -12%, a real +3pp change but still negative overall

        var result = calculate(null, null, null, marketSignal, pricePoint, null, null);

        assertThat(result.primaryState()).isEqualTo(RiskContradictionState.NO_CLEAR_SIGNAL);
    }

    @Test
    void priceWithoutDeliveryDoesNotFireWhenMarketStateIsMissingEntirely_missingEvidenceIsNotConfirmation() {
        var pricePoint = point(D1, "1.2", "3.5", 88.0);

        var result = calculate(null, null, null, null, pricePoint, null, null);

        assertThat(result.primaryState()).isEqualTo(RiskContradictionState.NO_CLEAR_SIGNAL);
    }

    @Test
    void priceWithoutDeliveryDoesNotFireWhenDeliveryRisingReasonIsPresent() {
        var marketSignal = signal(D1, 90.0, "DELIVERY_RISING");
        var pricePoint = point(D1, "1.2", "3.5", 88.0);

        var result = calculate(null, null, null, marketSignal, pricePoint, null, null);

        assertThat(result.primaryState()).isEqualTo(RiskContradictionState.NO_CLEAR_SIGNAL);
    }

    @Test
    void capitalRaiseWithWeakBusinessInflectionFiresWithinStalenessWindow() {
        var corporateSignal = signal(D1, 75.0, "EQUITY_RAISE_EVENT_COUNT_180D_NONZERO");
        var financialAsOf = signal(D1.minusDays(90), 75.0); // no REVENUE/PAT reasons, 90 days old

        var result = calculate(null, null, null, null, null, corporateSignal, financialAsOf);

        assertThat(result.primaryState()).isEqualTo(RiskContradictionState.CAPITAL_RAISE_WITH_WEAK_BUSINESS_INFLECTION);
        assertThat(result.confidence()).isEqualTo(75.0);
        assertThat(result.asOfDate()).isEqualTo(D1); // the anchor, not the stale financial read's own date
    }

    @Test
    void capitalRaiseWeakDoesNotFireWhenFinancialAsOfIsMissingEntirely_missingEvidenceIsNotConfirmation() {
        var corporateSignal = signal(D1, 75.0, "EQUITY_RAISE_EVENT_COUNT_180D_NONZERO");

        var result = calculate(null, null, null, null, null, corporateSignal, null);

        assertThat(result.primaryState()).isEqualTo(RiskContradictionState.NO_CLEAR_SIGNAL);
    }

    @Test
    void capitalRaiseWeakDoesNotFireWhenFinancialReadIsTooStale() {
        var corporateSignal = signal(D1, 75.0, "EQUITY_RAISE_EVENT_COUNT_180D_NONZERO");
        var financialAsOf = signal(D1.minusDays(121), 75.0); // 1 day past the 120-day staleness threshold

        var result = calculate(null, null, null, null, null, corporateSignal, financialAsOf);

        assertThat(result.primaryState()).isEqualTo(RiskContradictionState.NO_CLEAR_SIGNAL);
    }

    @Test
    void multiDomainContradictionFiresAtTwoWithMinimumConfidenceAndUnionedReasons() {
        var ownershipSignal = signal(D1, 81.0, "OWNERSHIP_CONTRADICTION");
        var financialState = signal(D2, 80.0, "REVENUE_GROWTH_IMPROVING");
        var marginPoint = point(D2, null, "-1.50", 70.0);

        var result = calculate(ownershipSignal, financialState, marginPoint, null, null, null, null);

        assertThat(result.primaryState()).isEqualTo(RiskContradictionState.MULTI_DOMAIN_CONTRADICTION);
        assertThat(result.confidence()).isEqualTo(70.0); // min(81, min(80,70)=70)
        assertThat(result.asOfDate()).isEqualTo(D1); // max(D1, D2)
        assertThat(result.reasons()).extracting("code").contains("OWNERSHIP_CONTRADICTION", "REVENUE_UP_MARGIN_DOWN", "MULTIPLE_CONTRADICTIONS");
    }

    @Test
    void noClearSignalAveragesConfidenceAcrossEveryReadThatReturnedDataAndUsesMaxDate() {
        var ownershipSignal = signal(D2, 90.0);
        var financialState = signal(D1, 70.0);

        var result = calculate(ownershipSignal, financialState, null, null, null, null, null);

        assertThat(result.primaryState()).isEqualTo(RiskContradictionState.NO_CLEAR_SIGNAL);
        assertThat(result.confidence()).isEqualTo(80.0); // (90 + 70) / 2
        assertThat(result.asOfDate()).isEqualTo(D1); // max(D2, D1)
    }

    @Test
    void evidenceCoverageAndReadinessReflectHowManyOfTheSixReadsReturnedData() {
        var ownershipSignal = signal(D1, 90.0);
        var financialState = signal(D1, 70.0);
        var marginPoint = point(D1, null, "0.5", 70.0);
        var marketSignal = signal(D1, 90.0);
        var pricePoint = point(D1, "-1.0", "0.2", 90.0);
        var corporateSignal = signal(D1, 75.0);

        var result = calculate(ownershipSignal, financialState, marginPoint, marketSignal, pricePoint, corporateSignal, null);

        assertThat(result.evidenceCoveragePct()).isEqualTo(100);
        assertThat(result.dataReadiness()).isEqualTo(RiskContradictionReadiness.READY);
    }

    @Test
    void evidenceCoverageIsPartialWhenOnlySomeSourcesHaveData() {
        var ownershipSignal = signal(D1, 90.0);

        var result = calculate(ownershipSignal, null, null, null, null, null, null);

        assertThat(result.evidenceCoveragePct()).isEqualTo(17); // 1/6
        assertThat(result.dataReadiness()).isEqualTo(RiskContradictionReadiness.PARTIAL_DATA);
    }

    private RiskContradictionResult calculate(
        FamilyStateSignal ownershipSignal, FamilyStateSignal financialState, RawMetricPoint marginPoint,
        FamilyStateSignal marketSignal, RawMetricPoint pricePoint, FamilyStateSignal corporateSignal, FamilyStateSignal financialAsOfAnchor
    ) {
        return engine.calculate(INSTRUMENT_ID, SYMBOL, ownershipSignal, financialState, marginPoint, marketSignal, pricePoint, corporateSignal, financialAsOfAnchor);
    }

    private static FamilyStateSignal signal(LocalDate asOfDate, double confidence, String... reasonCodes) {
        List<ReasonCode> reasons = List.of(reasonCodes).stream().map(ReasonCode::of).toList();
        return new FamilyStateSignal(asOfDate, confidence, reasons);
    }

    private static RawMetricPoint point(LocalDate asOfDate, String value, String change, double confidence) {
        return new RawMetricPoint(asOfDate, value == null ? null : new BigDecimal(value), change == null ? null : new BigDecimal(change), confidence);
    }
}
