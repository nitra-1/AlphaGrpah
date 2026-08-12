package com.alphagraph.learning.outcomes;

import com.alphagraph.decision.api.DecisionRating;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * What actually happened {@code horizonDays} trading days after a decision snapshot -
 * one row per (instrument, decision date, horizon). The {@code *DirectionallyCorrect}/
 * {@code *SignalCorrect} fields are {@code null}, not {@code false}, whenever the underlying
 * rating/score gave no clear direction (HOLD, or a domain score of exactly 50/missing) - an
 * honest "not applicable", never forced into a wrong bucket.
 */
public record ForwardOutcome(
    UUID instrumentId, String symbol, LocalDate asOfDate, int horizonDays, LocalDate outcomeDate,
    BigDecimal referencePrice, BigDecimal outcomePrice, BigDecimal forwardReturnPercentage,
    DecisionRating swingRating, Boolean swingDirectionallyCorrect,
    DecisionRating longTermRating, Boolean longTermDirectionallyCorrect,
    Boolean technicalSignalCorrect, Boolean fundamentalSignalCorrect, Boolean institutionalSignalCorrect,
    Boolean sectorSignalCorrect, Boolean riskSignalCorrect, Boolean corporateSignalCorrect
) {
}
