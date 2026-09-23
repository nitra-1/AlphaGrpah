package com.alphagraph.corporate.transformation;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One sequence type's resolved cluster state as of {@code asOfDate} - matches
 * docs/008_Stage3_Sequence_Detection_Specification.md §13's shared conceptual shape, reinterpreted
 * for a repeat-count model rather than a distinct-reason-code chain (see
 * {@code CapitalAllocationTransformationSequenceEngine}'s javadoc). {@code totalSteps} is the
 * configured required-repeat-count; {@code currentStep} is
 * {@code min(observedOccurrences, totalSteps)}. {@code observedOccurrences} is the real, uncapped
 * count of qualifying occurrences in the active cluster - the minimum number of entries inferable
 * from positive net {@code change} values, not a guaranteed exact event count (see the engine's
 * javadoc for the disclosed net-change limitation). {@code firstStepDate}/{@code lastStepDate} are
 * the real occurrence evidence dates for the active cluster, refreshed on every in-window
 * occurrence including ones after {@code COMPLETE} is first reached. Only ever constructed once a
 * cluster has actually started forming - genuine silence never produces a result at all, distinct
 * from {@link CapitalAllocationSequenceReadinessResult}, which is always computed.
 */
record CapitalAllocationSequenceResult(
    UUID instrumentId, String symbol, LocalDate asOfDate,
    CapitalAllocationSequenceType sequenceType, CapitalAllocationSequencePhase sequencePhase,
    int currentStep, int totalSteps, int observedOccurrences, LocalDate firstStepDate, LocalDate lastStepDate,
    double sequenceStrength, double confidence, int ruleVersion, List<ReasonCode> reasons
) {
}
