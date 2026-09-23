package com.alphagraph.discovery.convergence;

import java.time.LocalDate;

/**
 * One real Stage 3 {@code sequence_type} row, the most recent as-of a given date, read directly
 * from a source domain's own {@code transformation_sequences} table. {@code lastStepDate} is the
 * real evidence-advancement date - the one freshness/recency must key off (never {@code
 * asOfDate}, which is merely when this row happened to be last evaluated/re-written).
 */
record SequenceRow(
    String sequenceType, SequencePhase phase, int currentStep, int totalSteps,
    LocalDate firstStepDate, LocalDate lastStepDate, double sequenceStrength, double confidence, LocalDate asOfDate
) {
}
