package com.alphagraph.sector.transformation;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One sequence type's resolved state as of {@code asOfDate} - matches
 * docs/008_Stage3_Sequence_Detection_Specification.md §13's shared conceptual shape.
 * {@code firstStepDate}/{@code lastStepDate} are the real underlying evidence dates for those
 * steps (see {@code SectorTransformationSequenceEngine}'s Decision-1 javadoc for why these are
 * never simply the Stage 2 row's own {@code as_of_date}). Only ever constructed once a sequence has
 * actually started forming - genuine silence never produces a result at all, distinct from
 * {@link SectorSequenceReadinessResult}, which is always computed.
 */
record SectorSequenceResult(
    UUID instrumentId, String symbol, LocalDate asOfDate,
    SectorSequenceType sequenceType, SectorSequencePhase sequencePhase,
    int currentStep, int totalSteps, LocalDate firstStepDate, LocalDate lastStepDate,
    double sequenceStrength, double confidence, int ruleVersion, List<ReasonCode> reasons
) {
}
