package com.alphagraph.market.transformation;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One sequence type's resolved state as of {@code asOfDate} - matches
 * docs/008_Stage3_Sequence_Detection_Specification.md §13's shared conceptual shape. Only ever
 * constructed when the sequence has actually started forming at some point in the lookback window
 * (see {@code MarketTransformationSequenceEngine}'s javadoc) - genuine silence never produces a
 * result at all, distinct from {@link MarketSequenceReadinessResult}, which is always computed.
 */
record MarketSequenceResult(
    UUID instrumentId, String symbol, LocalDate asOfDate,
    MarketSequenceType sequenceType, MarketSequencePhase sequencePhase,
    int currentStep, int totalSteps, LocalDate firstStepDate, LocalDate lastStepDate,
    double sequenceStrength, double confidence, int ruleVersion, List<ReasonCode> reasons
) {
}
