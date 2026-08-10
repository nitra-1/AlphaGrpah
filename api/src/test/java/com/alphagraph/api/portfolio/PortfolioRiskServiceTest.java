package com.alphagraph.api.portfolio;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortfolioRiskServiceTest {

    private final PortfolioViewService viewService = mock(PortfolioViewService.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final PortfolioRiskService riskService = new PortfolioRiskService(viewService, jdbcTemplate);
    private final UUID userId = UUID.randomUUID();

    @Test
    void emptyPortfolioProducesAnAllNullResultRatherThanDividingByZero() {
        when(viewService.list(userId)).thenReturn(List.of());

        PortfolioRiskDto dto = riskService.compute(userId);

        assertThat(dto.totalMarketValue()).isNull();
        assertThat(dto.weightedRiskScore()).isNull();
        assertThat(dto.topHoldingSymbol()).isNull();
        assertThat(dto.sectorBreakdown()).isEmpty();
    }

    @Test
    void unpricedHoldingsAreExcludedEntirely() {
        UUID id = UUID.randomUUID();
        when(viewService.list(userId)).thenReturn(List.of(entry(id, "NEWCO", null, null)));

        PortfolioRiskDto dto = riskService.compute(userId);

        assertThat(dto.totalMarketValue()).isNull();
    }

    @Test
    void aSingleHoldingIsOneHundredPercentConcentratedAndVeryHighBand() {
        UUID id = UUID.randomUUID();
        when(viewService.list(userId)).thenReturn(List.of(entry(id, "TCS", new BigDecimal("10000"), 75.0)));
        stubSector(id, "Information Technology");

        PortfolioRiskDto dto = riskService.compute(userId);

        assertThat(dto.totalMarketValue()).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(dto.topHoldingSymbol()).isEqualTo("TCS");
        assertThat(dto.topHoldingConcentrationPercent()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(dto.holdingConcentrationLevel()).isEqualTo("VERY_HIGH");
        assertThat(dto.topSectorName()).isEqualTo("Information Technology");
        assertThat(dto.sectorConcentrationLevel()).isEqualTo("VERY_HIGH");
        assertThat(dto.weightedRiskScore()).isEqualTo(75.0);
        assertThat(dto.weightedRiskLevel()).isEqualTo("LOW"); // 75 -> LOW band (>=60)
        assertThat(dto.riskScoreCoveragePercent()).isEqualTo(100.0);
    }

    @Test
    void weightedRiskScoreIsGenuinelyMarketValueWeightedNotASimpleAverage() {
        UUID big = UUID.randomUUID();
        UUID small = UUID.randomUUID();
        // Big position (9000, risk 90) should dominate a naive average of (90+10)/2=50.
        when(viewService.list(userId)).thenReturn(List.of(
            entry(big, "TCS", new BigDecimal("9000"), 90.0),
            entry(small, "BEML", new BigDecimal("1000"), 10.0)
        ));
        stubSector(big, "Information Technology");
        stubSector(small, "Industrials");

        PortfolioRiskDto dto = riskService.compute(userId);

        // (9000*90 + 1000*10) / 10000 = 82.0
        assertThat(dto.weightedRiskScore()).isEqualTo(82.0);
        assertThat(dto.riskScoreCoveragePercent()).isEqualTo(100.0);
    }

    @Test
    void weightedRiskScoreIsRoundedToTwoDecimalPlacesRatherThanLeakingFloatingPointNoise() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        // (1000*10 + 2000*15) / 3000 = 40000/3000 = 13.3333... - deliberately not a clean decimal.
        when(viewService.list(userId)).thenReturn(List.of(
            entry(a, "INFY", new BigDecimal("1000"), 10.0),
            entry(b, "RELIANCE", new BigDecimal("2000"), 15.0)
        ));
        stubSector(a, "Information Technology");
        stubSector(b, "Energy");

        PortfolioRiskDto dto = riskService.compute(userId);

        assertThat(dto.weightedRiskScore()).isEqualTo(13.33);
    }

    @Test
    void holdingsWithNoRiskScoreAreExcludedFromTheWeightedAverageButCountTowardConcentration() {
        UUID scored = UUID.randomUUID();
        UUID unscored = UUID.randomUUID();
        when(viewService.list(userId)).thenReturn(List.of(
            entry(scored, "TCS", new BigDecimal("5000"), 80.0),
            entry(unscored, "NEWCO", new BigDecimal("5000"), null)
        ));
        stubSector(scored, "Information Technology");
        stubSector(unscored, "Information Technology");

        PortfolioRiskDto dto = riskService.compute(userId);

        assertThat(dto.totalMarketValue()).isEqualByComparingTo(new BigDecimal("10000")); // both count toward total
        assertThat(dto.weightedRiskScore()).isEqualTo(80.0); // only the scored one contributes
        assertThat(dto.riskScoreCoveragePercent()).isEqualTo(50.0); // only half the value is reflected
    }

    @Test
    void sectorExposureAggregatesAcrossMultipleHoldingsInTheSameSector() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(viewService.list(userId)).thenReturn(List.of(
            entry(a, "TCS", new BigDecimal("6000"), 80.0),
            entry(b, "INFY", new BigDecimal("4000"), 70.0)
        ));
        stubSector(a, "Information Technology");
        stubSector(b, "Information Technology");

        PortfolioRiskDto dto = riskService.compute(userId);

        assertThat(dto.sectorBreakdown()).hasSize(1);
        assertThat(dto.sectorBreakdown().get(0).sectorName()).isEqualTo("Information Technology");
        assertThat(dto.sectorBreakdown().get(0).marketValue()).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(dto.topSectorConcentrationPercent()).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    void anInstrumentWithNoSectorAssignedFallsBackToUnclassifiedRatherThanBeingDropped() {
        UUID id = UUID.randomUUID();
        when(viewService.list(userId)).thenReturn(List.of(entry(id, "NEWCO", new BigDecimal("1000"), 50.0)));
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq(id))).thenReturn(List.of());

        PortfolioRiskDto dto = riskService.compute(userId);

        assertThat(dto.topSectorName()).isEqualTo("Unclassified");
    }

    private void stubSector(UUID instrumentId, String sectorName) {
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq(instrumentId))).thenReturn(List.of(sectorName));
    }

    private PortfolioEntryDto entry(UUID instrumentId, String symbol, BigDecimal marketValue, Double riskScore) {
        return new PortfolioEntryDto(
            instrumentId, symbol, BigDecimal.TEN, BigDecimal.TEN,
            null, null, marketValue, null, null,
            null, null, null, null, null, null,
            riskScore == null ? null : "LOW", riskScore
        );
    }
}
