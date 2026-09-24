package com.alphagraph.api.opportunities;

import java.time.LocalDate;

/** Mirrors {@code decision.api.LifecycleTransition} exactly - one real Stage 5 state change. */
public record LifecycleTransitionDto(LocalDate transitionDate, String fromState, String toState, String triggerReason, Double convergenceScore, Integer activeDomainCount) {
}
