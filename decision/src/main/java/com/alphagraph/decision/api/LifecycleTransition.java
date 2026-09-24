package com.alphagraph.decision.api;

import java.time.LocalDate;
import java.util.UUID;

/** decision's own copy of one row from {@code discovery.lifecycle_transitions} - the "when did X become EMERGING" timeline. */
public record LifecycleTransition(
    UUID instrumentId, String symbol, LocalDate transitionDate,
    String fromState, String toState, String triggerReason,
    Double convergenceScore, Integer activeDomainCount
) {
}
