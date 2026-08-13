package com.alphagraph.learning.outcomes;

import com.alphagraph.decision.api.DecisionRating;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * What actually happened {@code horizonDays} trading days after a decision snapshot -
 * one row per (instrument, decision date, horizon). The {@code *DirectionallyCorrect}/
 * {@code *SignalCorrect} fields are {@code null}, not {@code false}, whenever the underlying
 * rating/score gave no clear direction (HOLD, or a domain score of exactly 50/missing) - an
 * honest "not applicable", never forced into a wrong bucket.
 *
 * <p>Outcome Evidence Enrichment: {@code status} distinguishes this DERIVED measurement from the
 * IMMUTABLE {@code DecisionSnapshot} it was computed from - {@code CURRENT} until a later-ingested
 * BONUS/SPLIT changes the adjusted price basis it was computed against (detected by comparing
 * {@code priceAdjustmentWatermark} - the newest BONUS/SPLIT {@code created_at} known at computation
 * time - against real corporate.corporate_actions data), at which point the whole row is marked
 * {@code INVALIDATED} and recomputed atomically (absolute return, benchmark-relative return, and
 * MFE/MAE together, never just one piece), never {@code recomputed_at} without a fresh
 * {@code CURRENT} status.
 *
 * <p>The benchmark-relative fields are independently nullable: {@code marketBenchmark*} requires a
 * real tracked index instrument (e.g. NIFTY 50); {@code sectorBenchmark*} requires a verified
 * {@code reference.sector_benchmarks} mapping for the instrument's sector, which not every sector
 * has - both are {@code null} rather than fabricated when the underlying data doesn't exist, and
 * the {@code *BenchmarkOutcomeDate} fields record the exact trading date the benchmark return was
 * measured on, so an exact-date-alignment mismatch (e.g. a holiday gap) is never silently smoothed
 * over.
 */
public record ForwardOutcome(
    UUID instrumentId, String symbol, LocalDate asOfDate, int horizonDays, LocalDate outcomeDate,
    BigDecimal referencePrice, BigDecimal outcomePrice, BigDecimal forwardReturnPercentage,
    DecisionRating swingRating, Boolean swingDirectionallyCorrect,
    DecisionRating longTermRating, Boolean longTermDirectionallyCorrect,
    Boolean technicalSignalCorrect, Boolean fundamentalSignalCorrect, Boolean institutionalSignalCorrect,
    Boolean sectorSignalCorrect, Boolean riskSignalCorrect, Boolean corporateSignalCorrect,
    String status, Instant priceAdjustmentWatermark, Instant recomputedAt,
    UUID marketBenchmarkInstrumentId, BigDecimal marketBenchmarkReturnPercentage, LocalDate marketBenchmarkOutcomeDate,
    BigDecimal excessReturnMarketPercentage,
    UUID sectorBenchmarkInstrumentId, BigDecimal sectorBenchmarkReturnPercentage, LocalDate sectorBenchmarkOutcomeDate,
    BigDecimal excessReturnSectorPercentage,
    BigDecimal mfePercentage, BigDecimal maePercentage
) {
}
