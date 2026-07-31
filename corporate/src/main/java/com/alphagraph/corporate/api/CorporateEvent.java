package com.alphagraph.corporate.api;

import java.time.Instant;
import java.util.UUID;

/**
 * One detected corporate event, mirroring {@code corporate.corporate_events}
 * (docs/003_Database_Architecture.md §3a). Produced by the LLM-backed Corporate Event Engine
 * (Module 2.3), not a rule-driven {@code common.engine.Score} - see
 * {@code com.alphagraph.corporate.events.CorporateEventEngine} for why. {@code promptVersion}
 * plays the same reproducibility-tracking role {@code ruleSetVersion} plays on every Phase 1
 * engine's {@code Score}: if the classification prompt changes, results carry a different
 * version rather than being silently indistinguishable from before.
 */
public record CorporateEvent(
    UUID id, UUID documentId, UUID instrumentId, String symbol,
    EventType eventType, String category, String summary,
    double confidence, String expectedDuration, RevenueImpact revenueImpact, EventSignal signal,
    int promptVersion, Instant extractedAt
) {
}
