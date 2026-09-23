package com.alphagraph.discovery.convergence;

import java.time.LocalDate;

/** One qualifying (non-{@code BROKEN}) sequence's own contribution within a domain - preserved individually for explainability even though only the domain's single representative (max-strength) sequence drives {@code domainStrength}. {@code contributionValue = sequenceStrength * phaseFactor * (confidence/100)} (user's Stage 4 spec §48). */
record SequenceContribution(
    String sequenceType, SequencePhase phase, double sequenceStrength, double confidence,
    LocalDate firstStepDate, LocalDate lastStepDate, double contributionValue
) {
}
