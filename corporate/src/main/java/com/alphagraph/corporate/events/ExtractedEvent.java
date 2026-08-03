package com.alphagraph.corporate.events;

import com.alphagraph.corporate.api.EventSignal;
import com.alphagraph.corporate.api.EventType;
import com.alphagraph.corporate.api.RevenueImpact;

/**
 * One event as classified by {@link CorporateEventEngine}, before it has a document/instrument
 * identity or a persisted id - {@link CorporateEventWriter} attaches those at persistence time.
 * {@code provenance} is a small JSON descriptor of which canonical topic/fact triggered this
 * classification, for auditability - it replaces the pre-retrofit raw Claude response, since this
 * engine no longer calls Claude itself.
 */
record ExtractedEvent(
    EventType eventType, String category, String summary, double confidence,
    String expectedDuration, RevenueImpact revenueImpact, EventSignal signal, String provenance
) {
}
