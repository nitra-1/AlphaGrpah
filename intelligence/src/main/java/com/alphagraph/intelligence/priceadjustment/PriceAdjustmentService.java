package com.alphagraph.intelligence.priceadjustment;

import com.alphagraph.corporate.api.CorporateAction;
import com.alphagraph.corporate.api.CorporateActionsReader;
import com.alphagraph.market.api.DailyPrice;
import com.alphagraph.market.api.DailyPriceReader;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Bridges market's raw price history and corporate's action history through {@link PriceAdjustmentEngine} - the one place this cross-domain join happens, per docs/001_System_Architecture.md §4 Rule 4. */
@Service
public class PriceAdjustmentService {

    private final DailyPriceReader dailyPriceReader;
    private final CorporateActionsReader corporateActionsReader;
    private final PriceAdjustmentEngine engine;

    public PriceAdjustmentService(DailyPriceReader dailyPriceReader, CorporateActionsReader corporateActionsReader, PriceAdjustmentEngine engine) {
        this.dailyPriceReader = dailyPriceReader;
        this.corporateActionsReader = corporateActionsReader;
        this.engine = engine;
    }

    /** Full back-adjusted price history for one instrument, ordered ascending by trade date - raw and adjusted close side by side, never just the adjusted figure alone. */
    public List<AdjustedDailyPrice> adjustedHistory(UUID instrumentId) {
        List<DailyPrice> prices = dailyPriceReader.findHistory(instrumentId);
        List<CorporateAction> actions = corporateActionsReader.findPriceAffectingActions(instrumentId);
        return engine.apply(prices, actions);
    }

    /** BONUS/SPLIT actions across the whole tracked universe in the last {@code lookbackDays} days - the dashboard "announce" surface, not applied to anything, just surfaced. */
    public List<CorporateAction> recentPriceAffectingActions(int lookbackDays) {
        return corporateActionsReader.findRecentPriceAffectingActions(lookbackDays);
    }

    /**
     * BONUS/SPLIT actions for one instrument - the same data {@link #adjustedHistory} already
     * pulls internally, exposed directly so {@code learning.outcomes.ForwardOutcomeInvalidator}
     * can detect a later-ingested action without querying {@code corporate.corporate_actions}
     * itself (per docs/001_System_Architecture.md §4 Rule 4, only this module may cross that
     * boundary).
     */
    public List<CorporateAction> findPriceAffectingActions(UUID instrumentId) {
        return corporateActionsReader.findPriceAffectingActions(instrumentId);
    }
}
