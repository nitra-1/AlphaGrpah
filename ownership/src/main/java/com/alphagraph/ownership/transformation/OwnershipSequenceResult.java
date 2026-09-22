package com.alphagraph.ownership.transformation;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One sequence type's resolved state as of {@code asOfDate} - matches
 * docs/008_Stage3_Sequence_Detection_Specification.md §13's shared conceptual shape. Only ever
 * constructed once a sequence has actually started forming at some point in the lookback window -
 * genuine silence never produces a result at all, distinct from {@link OwnershipSequenceReadinessResult},
 * which is always computed.
 */
record OwnershipSequenceResult(
    UUID instrumentId, String symbol, LocalDate asOfDate,
    OwnershipSequenceType sequenceType, OwnershipSequencePhase sequencePhase,
    int currentStep, int totalSteps, LocalDate firstStepDate, LocalDate lastStepDate,
    double sequenceStrength, double confidence, int ruleVersion, List<ReasonCode> reasons
) {
}
