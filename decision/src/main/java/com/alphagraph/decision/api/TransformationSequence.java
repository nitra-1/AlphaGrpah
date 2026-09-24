package com.alphagraph.decision.api;

import java.time.LocalDate;
import java.util.List;

/** One Stage 3 transformation sequence row for one domain, resolved as of a given anchor date (never an unconditional "latest"). */
public record TransformationSequence(
    String sequenceType, String sequencePhase, int currentStep, int totalSteps,
    LocalDate firstStepDate, LocalDate lastStepDate, Double sequenceStrength, Double confidence,
    List<ReasonNote> reasons
) {
}
