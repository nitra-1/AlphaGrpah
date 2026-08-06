package com.alphagraph.decision.api;

import java.time.Instant;
import java.util.UUID;

/**
 * One instrument on the single global watchlist (Module 3.2) - not a {@code common.engine.Score},
 * just a persisted "we're watching this" record. Enriching it with a current signal (Swing/Long-Term
 * Score, Rank) is an API-layer concern (api.watchlist assembles that from {@link DecisionScore} via
 * {@code decision.engine.DecisionScoreReader}), the same separation {@code corporate.api.CorporateEvent}
 * and its dashboard DTO already have.
 */
public record WatchlistItem(UUID id, UUID instrumentId, String symbol, Instant addedAt) {
}
