package com.alphagraph.corporate.events;

import com.alphagraph.corporate.api.EventSignal;
import com.alphagraph.corporate.api.EventType;
import com.alphagraph.corporate.api.RevenueImpact;

/**
 * One event as classified by {@link CorporateEventEngine}, before it has a document/instrument
 * identity or a persisted id - {@link CorporateEventWriter} attaches those at persistence time.
 * {@code rawResponse} carries the engine's full JSON response verbatim, for auditability.
 */
record ExtractedEvent(
    EventType eventType, String category, String summary, int confidence,
    String expectedDuration, RevenueImpact revenueImpact, EventSignal signal, String rawResponse
) {
}
