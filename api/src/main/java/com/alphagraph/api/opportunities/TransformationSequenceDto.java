package com.alphagraph.api.opportunities;

import java.time.LocalDate;
import java.util.List;

/** Mirrors {@code decision.api.TransformationSequence} exactly - one Stage 3 sequence row, resolved as of the containing detail's own {@code asOfDate}. */
public record TransformationSequenceDto(
    String sequenceType, String sequencePhase, int currentStep, int totalSteps,
    LocalDate firstStepDate, LocalDate lastStepDate, Double sequenceStrength, Double confidence,
    List<ReasonNoteDto> reasons
) {
}
