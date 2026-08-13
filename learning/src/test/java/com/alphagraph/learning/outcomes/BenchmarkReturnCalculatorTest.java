package com.alphagraph.learning.outcomes;

import com.alphagraph.intelligence.priceadjustment.AdjustedDailyPrice;
import com.alphagraph.intelligence.priceadjustment.PriceAdjustmentService;
import com.alphagraph.reference.instrument.InstrumentReader;
import com.alphagraph.reference.instrument.SectorBenchmarkReader;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BenchmarkReturnCalculatorTest {

    private static final String MARKET_SYMBOL = "NIFTY50";

    private final InstrumentReader instrumentReader = mock(InstrumentReader.class);
    private final SectorBenchmarkReader sectorBenchmarkReader = mock(SectorBenchmarkReader.class);
    private final PriceAdjustmentService priceAdjustmentService = mock(PriceAdjustmentService.class);
    private final BenchmarkReturnCalculator calculator =
        new BenchmarkReturnCalculator(instrumentReader, sectorBenchmarkReader, priceAdjustmentService, MARKET_SYMBOL);

    private final UUID instrumentId = UUID.randomUUID();
    private final UUID marketBenchmarkId = UUID.randomUUID();
    private final LocalDate asOfDate = LocalDate.of(2026, 8, 1);
    private final LocalDate outcomeDate = LocalDate.of(2026, 8, 8);

    @Test
    void computesMarketExcessReturnWhenBenchmarkHasExactDateRowsOnBothEnds() {
        when(instrumentReader.findIdBySymbol(MARKET_SYMBOL)).thenReturn(Optional.of(marketBenchmarkId));
        when(priceAdjustmentService.adjustedHistory(marketBenchmarkId)).thenReturn(List.of(
            price(asOfDate, "1000.00"), price(outcomeDate, "1050.00")
        ));

        BenchmarkReturnCalculator.BenchmarkResult result = calculator.computeMarket(asOfDate, outcomeDate, new BigDecimal("10.00"));

        assertThat(result.benchmarkInstrumentId()).isEqualTo(marketBenchmarkId);
        assertThat(result.returnPercentage()).isEqualByComparingTo("5.00");
        assertThat(result.outcomeDate()).isEqualTo(outcomeDate);
        assertThat(result.excessReturnPercentage()).isEqualByComparingTo("5.00");
    }

    @Test
    void isUnavailableWhenTheMarketBenchmarkSymbolIsNotTrackedYet() {
        when(instrumentReader.findIdBySymbol(MARKET_SYMBOL)).thenReturn(Optional.empty());

        BenchmarkReturnCalculator.BenchmarkResult result = calculator.computeMarket(asOfDate, outcomeDate, new BigDecimal("10.00"));

        assertThat(result).isEqualTo(BenchmarkReturnCalculator.BenchmarkResult.UNAVAILABLE);
    }

    @Test
    void isUnavailableRatherThanSubstitutedWhenTheBenchmarkIsMissingTheExactOutcomeDate() {
        when(instrumentReader.findIdBySymbol(MARKET_SYMBOL)).thenReturn(Optional.of(marketBenchmarkId));
        // Benchmark has a row the day before and after outcomeDate, but not on it exactly (e.g. a holiday
        // mismatch) - must come back UNAVAILABLE, never silently substituted from a nearby date.
        when(priceAdjustmentService.adjustedHistory(marketBenchmarkId)).thenReturn(List.of(
            price(asOfDate, "1000.00"), price(outcomeDate.minusDays(1), "1040.00"), price(outcomeDate.plusDays(1), "1060.00")
        ));

        BenchmarkReturnCalculator.BenchmarkResult result = calculator.computeMarket(asOfDate, outcomeDate, new BigDecimal("10.00"));

        assertThat(result).isEqualTo(BenchmarkReturnCalculator.BenchmarkResult.UNAVAILABLE);
    }

    @Test
    void isUnavailableForSectorWhenNoVerifiedSectorMappingExists() {
        when(sectorBenchmarkReader.findBenchmarkInstrumentIdForInstrument(instrumentId)).thenReturn(Optional.empty());

        BenchmarkReturnCalculator.BenchmarkResult result = calculator.computeSector(instrumentId, asOfDate, outcomeDate, new BigDecimal("10.00"));

        assertThat(result).isEqualTo(BenchmarkReturnCalculator.BenchmarkResult.UNAVAILABLE);
    }

    @Test
    void computesSectorExcessReturnWhenAVerifiedMappingExists() {
        UUID sectorBenchmarkId = UUID.randomUUID();
        when(sectorBenchmarkReader.findBenchmarkInstrumentIdForInstrument(instrumentId)).thenReturn(Optional.of(sectorBenchmarkId));
        when(priceAdjustmentService.adjustedHistory(sectorBenchmarkId)).thenReturn(List.of(
            price(asOfDate, "500.00"), price(outcomeDate, "480.00")
        ));

        BenchmarkReturnCalculator.BenchmarkResult result = calculator.computeSector(instrumentId, asOfDate, outcomeDate, new BigDecimal("10.00"));

        assertThat(result.returnPercentage()).isEqualByComparingTo("-4.00");
        assertThat(result.excessReturnPercentage()).isEqualByComparingTo("14.00");
    }

    private AdjustedDailyPrice price(LocalDate tradeDate, String close) {
        BigDecimal value = new BigDecimal(close);
        return new AdjustedDailyPrice(marketBenchmarkId, "BENCH", tradeDate, value, value, value, value, value, value, BigDecimal.ONE, List.of());
    }
}
