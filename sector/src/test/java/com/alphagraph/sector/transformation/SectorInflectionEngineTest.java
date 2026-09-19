package com.alphagraph.sector.transformation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class SectorInflectionEngineTest {

    private static final UUID INSTRUMENT_ID = UUID.randomUUID();
    private static final String SYMBOL = "RELIANCE";
    private static final LocalDate D1 = LocalDate.of(2026, 9, 15);
    private static final LocalDate D2 = LocalDate.of(2026, 9, 16);

    private final SectorInflectionEngine engine = new SectorInflectionEngine();

    @Test
    void sectorStrengtheningFiresOnPositiveChange() {
        var sectorRs = obs(SectorMetric.SECTOR_RELATIVE_STRENGTH, D1, D2, "1.20", "0.90", "0.30", 3, 90.0);

        var result = engine.calculate(INSTRUMENT_ID, SYMBOL, sectorRs, null, null, 0, 0);

        assertThat(result.primaryState()).isEqualTo(SectorInflectionState.SECTOR_STRENGTHENING);
        assertThat(result.drivingMetric()).isEqualTo(SectorMetric.SECTOR_RELATIVE_STRENGTH);
        assertThat(result.level()).isEqualByComparingTo("1.20");
        assertThat(result.change()).isEqualByComparingTo("0.30");
        assertThat(result.persistence()).isEqualTo(3);
        assertThat(result.confidence()).isEqualTo(96.0); // 90 + min(10, 2*3)
        assertThat(result.evidenceCoveragePct()).isEqualTo(33);
        assertThat(result.dataReadiness()).isEqualTo(SectorDataReadiness.PARTIAL_DATA);
    }

    @Test
    void sectorStrengtheningPersistenceCappedAtFive() {
        var sectorRs = obs(SectorMetric.SECTOR_RELATIVE_STRENGTH, D1, D2, "1.20", "0.90", "0.30", 8, 90.0);

        var result = engine.calculate(INSTRUMENT_ID, SYMBOL, sectorRs, null, null, 0, 0);

        assertThat(result.persistence()).isEqualTo(5);
        assertThat(result.confidence()).isEqualTo(100.0); // 90 + min(10, 2*5)
    }

    @Test
    void stockOutperformingNiftyFiresWhenValuePositiveAndSectorSideAbsent() {
        var vsNifty = obs(SectorMetric.INSTRUMENT_RELATIVE_STRENGTH_VS_NIFTY, D1, D2, "1.5", "1.2", "0.3", 2, 90.0);

        var result = engine.calculate(INSTRUMENT_ID, SYMBOL, null, vsNifty, null, 3, 0);

        assertThat(result.primaryState()).isEqualTo(SectorInflectionState.STOCK_OUTPERFORMING_NIFTY);
        assertThat(result.drivingMetric()).isEqualTo(SectorMetric.INSTRUMENT_RELATIVE_STRENGTH_VS_NIFTY);
        assertThat(result.confidence()).isEqualTo(54.0); // depthTier(3)=50 + min(10, 2*2)
    }

    @Test
    void stockOutperformingSectorOutranksStockOutperformingNiftyWhenBothFire() {
        var vsNifty = obs(SectorMetric.INSTRUMENT_RELATIVE_STRENGTH_VS_NIFTY, D1, D2, "1.5", "1.2", "0.3", 2, 90.0);
        var vsSector = obs(SectorMetric.INSTRUMENT_RELATIVE_STRENGTH_VS_SECTOR, D1, D2, "0.8", "0.5", "0.3", 1, 90.0);

        var result = engine.calculate(INSTRUMENT_ID, SYMBOL, null, vsNifty, vsSector, 3, 10);

        assertThat(result.primaryState()).isEqualTo(SectorInflectionState.STOCK_OUTPERFORMING_SECTOR);
    }

    @Test
    void newLeadershipEmergenceFiresOnARealCrossingFromTheSameEvidenceRow() {
        var vsSector = obs(SectorMetric.INSTRUMENT_RELATIVE_STRENGTH_VS_SECTOR, D1, D2, "1.0", "-0.5", "1.5", 0, 90.0);

        var result = engine.calculate(INSTRUMENT_ID, SYMBOL, null, null, vsSector, 0, 12);

        assertThat(result.primaryState()).isEqualTo(SectorInflectionState.NEW_LEADERSHIP_EMERGENCE);
        assertThat(result.drivingMetric()).isEqualTo(SectorMetric.INSTRUMENT_RELATIVE_STRENGTH_VS_SECTOR);
        assertThat(result.persistence()).isEqualTo(0);
        assertThat(result.confidence()).isEqualTo(60.0); // depthTier(12)=60 + min(10, 2*0)
    }

    @Test
    void newLeadershipEmergenceDoesNotFireWhenAlreadyPositiveYesterday() {
        var vsSector = obs(SectorMetric.INSTRUMENT_RELATIVE_STRENGTH_VS_SECTOR, D1, D2, "1.0", "0.5", "0.5", 4, 90.0);

        var result = engine.calculate(INSTRUMENT_ID, SYMBOL, null, null, vsSector, 0, 12);

        assertThat(result.primaryState()).isEqualTo(SectorInflectionState.STOCK_OUTPERFORMING_SECTOR);
    }

    @Test
    void newLeadershipEmergenceDoesNotFireOnAFirstObservationWithNoRealPriorToCrossFrom() {
        var vsSector = obs(SectorMetric.INSTRUMENT_RELATIVE_STRENGTH_VS_SECTOR, D1, null, "1.0", null, null, 0, 40.0);

        var result = engine.calculate(INSTRUMENT_ID, SYMBOL, null, null, vsSector, 0, 1);

        assertThat(result.primaryState()).isEqualTo(SectorInflectionState.STOCK_OUTPERFORMING_SECTOR);
    }

    @Test
    void noClearSignalFallsBackToAveragedConfidenceAndMaxAsOfDate() {
        var sectorRs = obs(SectorMetric.SECTOR_RELATIVE_STRENGTH, D1, D2, "1.0", "1.0", "0.0", 0, 90.0);
        var vsNifty = obs(SectorMetric.INSTRUMENT_RELATIVE_STRENGTH_VS_NIFTY, D2, D1, "-0.2", "-0.1", "-0.1", 0, 40.0);

        var result = engine.calculate(INSTRUMENT_ID, SYMBOL, sectorRs, vsNifty, null, 1, 0);

        assertThat(result.primaryState()).isEqualTo(SectorInflectionState.NO_CLEAR_SIGNAL);
        assertThat(result.drivingMetric()).isNull();
        assertThat(result.asOfDate()).isEqualTo(D2); // max(D1, D2)
        assertThat(result.confidence()).isEqualTo(65.0); // (90 + 40) / 2
        assertThat(result.evidenceCoveragePct()).isEqualTo(67);
        assertThat(result.dataReadiness()).isEqualTo(SectorDataReadiness.PARTIAL_DATA);
    }

    @Test
    void evidenceCoverageIsFullyReadyWhenAllThreeSourcesArePresent_evenOnAFiringState() {
        var sectorRs = obs(SectorMetric.SECTOR_RELATIVE_STRENGTH, D1, D2, "1.2", "0.9", "0.3", 1, 90.0);
        var vsNifty = obs(SectorMetric.INSTRUMENT_RELATIVE_STRENGTH_VS_NIFTY, D1, D2, "-0.2", "-0.1", "-0.1", 0, 40.0);
        var vsSector = obs(SectorMetric.INSTRUMENT_RELATIVE_STRENGTH_VS_SECTOR, D1, D2, "-0.3", "-0.2", "-0.1", 0, 40.0);

        var result = engine.calculate(INSTRUMENT_ID, SYMBOL, sectorRs, vsNifty, vsSector, 6, 7);

        assertThat(result.primaryState()).isEqualTo(SectorInflectionState.SECTOR_STRENGTHENING);
        assertThat(result.evidenceCoveragePct()).isEqualTo(100);
        assertThat(result.dataReadiness()).isEqualTo(SectorDataReadiness.READY);
    }

    @Test
    void confidenceTierBoundariesForStockOutperformingNifty() {
        assertThat(baseConfidenceAt(0)).isCloseTo(40.0, offset(0.01));
        assertThat(baseConfidenceAt(1)).isCloseTo(50.0, offset(0.01));
        assertThat(baseConfidenceAt(4)).isCloseTo(50.0, offset(0.01));
        assertThat(baseConfidenceAt(5)).isCloseTo(60.0, offset(0.01));
        assertThat(baseConfidenceAt(19)).isCloseTo(60.0, offset(0.01));
        assertThat(baseConfidenceAt(20)).isCloseTo(75.0, offset(0.01));
    }

    /** persistence=0, hasPrior=true so confidence isolates the tiered base exactly. */
    private double baseConfidenceAt(int observationCount) {
        var vsNifty = obs(SectorMetric.INSTRUMENT_RELATIVE_STRENGTH_VS_NIFTY, D1, D2, "1.0", "1.0", "0.0", 0, 90.0);
        var result = engine.calculate(INSTRUMENT_ID, SYMBOL, null, vsNifty, null, observationCount, 0);
        assertThat(result.primaryState()).isEqualTo(SectorInflectionState.STOCK_OUTPERFORMING_NIFTY);
        return result.confidence();
    }

    private static SectorEvidenceObservation obs(
        SectorMetric metric, LocalDate asOfDate, LocalDate priorAsOfDate, String value, String priorValue, String change, int persistenceDays, double confidence
    ) {
        return new SectorEvidenceObservation(
            metric, INSTRUMENT_ID, SYMBOL, asOfDate, priorAsOfDate,
            new BigDecimal(value), priorValue == null ? null : new BigDecimal(priorValue), change == null ? null : new BigDecimal(change),
            persistenceDays, confidence
        );
    }
}
