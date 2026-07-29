package com.alphagraph.ownership.api;

import java.util.List;
import java.util.UUID;

/**
 * The Institutional Engine's input for one instrument: its shareholding history (ascending by
 * {@link ShareholdingPattern#periodEnd()}), recent market activity (ascending by trade date,
 * assembled by {@code intelligence} from market's published API), and any real bulk/block deals
 * found for it.
 */
public record InstitutionalEngineInput(
    UUID instrumentId, String symbol,
    List<ShareholdingPattern> shareholdingPeriodsAscending,
    List<DeliveryVolumeBar> recentMarketActivity,
    List<BulkDeal> recentBulkDeals
) {
    public InstitutionalEngineInput {
        shareholdingPeriodsAscending = List.copyOf(shareholdingPeriodsAscending);
        recentMarketActivity = List.copyOf(recentMarketActivity);
        recentBulkDeals = List.copyOf(recentBulkDeals);
    }
}
