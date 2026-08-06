package com.alphagraph.api.watchlist;

import java.time.Instant;
import java.util.UUID;

/** A watched instrument plus its current signal, if one has been computed yet - the score fields are null for an instrument added before any Module 3.1 decision score exists for it. */
public record WatchlistEntryDto(
    UUID instrumentId, String symbol, Instant addedAt,
    Double swingScore, String swingRating, Integer swingRank,
    Double longTermScore, String longTermRating, Integer longTermRank
) {
}
