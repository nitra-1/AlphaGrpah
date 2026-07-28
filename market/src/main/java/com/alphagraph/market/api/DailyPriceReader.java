package com.alphagraph.market.api;

import java.util.List;
import java.util.UUID;

/**
 * Read side of market's published API. Per docs/001_System_Architecture.md §4 Rule 4,
 * {@code intelligence} is the only module allowed to pull this data cross-domain (domain modules
 * like {@code technical} never depend on {@code market} directly - Rule 3), so this interface,
 * not {@code market.pricing}'s internal JDBC classes, is the seam {@code intelligence} builds
 * against.
 */
public interface DailyPriceReader {

    /** All instrument ids that have at least one row in market.daily_prices. */
    List<UUID> instrumentIdsWithHistory();

    /** Full daily price history for one instrument, ordered ascending by trade date. */
    List<DailyPrice> findHistory(UUID instrumentId);
}
