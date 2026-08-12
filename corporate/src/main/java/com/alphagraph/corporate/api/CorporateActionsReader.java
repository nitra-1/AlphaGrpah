package com.alphagraph.corporate.api;

import java.util.List;
import java.util.UUID;

/**
 * Read side of corporate's published API. Per docs/001_System_Architecture.md §4 Rule 4,
 * {@code intelligence} is the only module allowed to pull this data cross-domain, so this
 * interface, not {@code corporate.actions}'s internal JDBC classes, is the seam {@code
 * intelligence} builds against - mirrors {@code market.api.DailyPriceReader}'s role exactly.
 */
public interface CorporateActionsReader {

    /** BONUS/SPLIT actions for one instrument, ordered ascending by ex-date - the only action types a price adjustment factor applies to. */
    List<CorporateAction> findPriceAffectingActions(UUID instrumentId);

    /** BONUS/SPLIT actions across every instrument with at least one, ordered ascending by ex-date within each instrument. */
    List<CorporateAction> findAllPriceAffectingActions();

    /** BONUS/SPLIT actions with an ex-date in the last {@code lookbackDays} days, across every instrument - the dashboard "recent price adjustments" surface. */
    List<CorporateAction> findRecentPriceAffectingActions(int lookbackDays);
}
