package com.alphagraph.intelligence.institutional;

import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.market.api.DailyPrice;
import com.alphagraph.market.api.DailyPriceReader;
import com.alphagraph.ownership.api.BulkDeal;
import com.alphagraph.ownership.api.DeliveryVolumeBar;
import com.alphagraph.ownership.api.InstitutionalEngineInput;
import com.alphagraph.ownership.api.InstitutionalScore;
import com.alphagraph.ownership.api.ShareholdingPattern;
import com.alphagraph.ownership.engine.BulkDealsReader;
import com.alphagraph.ownership.engine.InstitutionalEngine;
import com.alphagraph.ownership.engine.InstitutionalRuleSetLoader;
import com.alphagraph.ownership.engine.InstitutionalScoreWriter;
import com.alphagraph.ownership.engine.ShareholdingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Runs the Institutional Engine over every instrument with at least one shareholding period.
 * Reads market's delivery%/volume through {@link DailyPriceReader} (market's published API,
 * never market's internal tables/classes) and ownership's own shareholding/bulk-deal data through
 * {@link ShareholdingReader}/{@link BulkDealsReader}, then persists via ownership's own
 * {@link InstitutionalScoreWriter} - this class does no direct SQL of its own.
 */
@Component
public class InstitutionalAnalysisOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(InstitutionalAnalysisOrchestrator.class);

    private final ShareholdingReader shareholdingReader;
    private final DailyPriceReader dailyPriceReader;
    private final BulkDealsReader bulkDealsReader;
    private final InstitutionalRuleSetLoader ruleSetLoader;
    private final InstitutionalEngine institutionalEngine;
    private final InstitutionalScoreWriter institutionalScoreWriter;

    public InstitutionalAnalysisOrchestrator(
        ShareholdingReader shareholdingReader, DailyPriceReader dailyPriceReader, BulkDealsReader bulkDealsReader,
        InstitutionalRuleSetLoader ruleSetLoader, InstitutionalEngine institutionalEngine,
        InstitutionalScoreWriter institutionalScoreWriter
    ) {
        this.shareholdingReader = shareholdingReader;
        this.dailyPriceReader = dailyPriceReader;
        this.bulkDealsReader = bulkDealsReader;
        this.ruleSetLoader = ruleSetLoader;
        this.institutionalEngine = institutionalEngine;
        this.institutionalScoreWriter = institutionalScoreWriter;
    }

    public void runForAllInstruments() {
        RuleSet rules = ruleSetLoader.loadActiveInstitutionalRules();
        List<UUID> instrumentIds = shareholdingReader.instrumentIdsWithShareholdingData();

        int computed = 0;
        for (UUID instrumentId : instrumentIds) {
            List<ShareholdingPattern> periods = shareholdingReader.findPeriods(instrumentId);
            if (periods.isEmpty()) {
                continue;
            }

            List<DailyPrice> priceHistory = dailyPriceReader.findHistory(instrumentId);
            List<DeliveryVolumeBar> marketActivity = priceHistory.stream()
                .map(p -> new DeliveryVolumeBar(p.tradeDate(), p.deliveryPercentage(), p.volume()))
                .toList();
            List<BulkDeal> bulkDeals = bulkDealsReader.findRecentDeals(instrumentId);

            InstitutionalEngineInput input = new InstitutionalEngineInput(
                instrumentId, periods.get(0).symbol(), periods, marketActivity, bulkDeals
            );
            InstitutionalScore score = institutionalEngine.calculate(input, rules);
            institutionalScoreWriter.write(score);
            computed++;
        }

        log.info("Institutional analysis computed for {} instrument(s), rule set version {}", computed, rules.version());
    }
}
