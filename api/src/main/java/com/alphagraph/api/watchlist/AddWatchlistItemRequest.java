package com.alphagraph.api.watchlist;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddWatchlistItemRequest(@NotNull UUID instrumentId) {
}
