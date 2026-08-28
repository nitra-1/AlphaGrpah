package com.alphagraph.ownership.deals;

import com.alphagraph.common.rules.RuleSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Drives {@link DealMaterialityEngine} over every unscored deal (see
 * {@link PendingMaterialityDealReader}). A deal whose symbol doesn't yet have 20 real trading
 * sessions of price history (see {@link MarketLiquidityReader}) is skipped, never guessed at with
 * a partial average - it simply reappears as pending on tomorrow's run once its symbol's backfill
 * (see {@code market.pricing.MarketPriceBackfillOrchestrator}) or normal daily capture catches up.
 */
@Component
class DealMaterialityScoringOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(DealMaterialityScoringOrchestrator.class);

    private final PendingMaterialityDealReader pendingDealReader;
    private final MarketLiquidityReader marketLiquidityReader;
    private final BulkDealContextReader bulkDealContextReader;
    private final DealMaterialityRuleSetLoader ruleSetLoader;
    private final DealMaterialityEngine engine;
    private final DealMaterialityWriter writer;

    DealMaterialityScoringOrchestrator(
        PendingMaterialityDealReader pendingDealReader, MarketLiquidityReader marketLiquidityReader,
        BulkDealContextReader bulkDealContextReader, DealMaterialityRuleSetLoader ruleSetLoader,
        DealMaterialityEngine engine, DealMaterialityWriter writer
    ) {
        this.pendingDealReader = pendingDealReader;
        this.marketLiquidityReader = marketLiquidityReader;
        this.bulkDealContextReader = bulkDealContextReader;
        this.ruleSetLoader = ruleSetLoader;
        this.engine = engine;
        this.writer = writer;
    }

    void scorePendingDeals() {
        List<PendingMaterialityDeal> pending = pendingDealReader.findUnscored();
        if (pending.isEmpty()) {
            log.info("Deal materiality scoring: no unscored deals.");
            return;
        }

        RuleSet rules = ruleSetLoader.loadActiveRules();
        int scored = 0;
        int skippedInsufficientHistory = 0;
        int failed = 0;

        for (PendingMaterialityDeal deal : pending) {
            try {
                Optional<BigDecimal> adtv20 = marketLiquidityReader.findAdtv20(deal.symbol(), deal.dealDate());
                if (adtv20.isEmpty()) {
                    skippedInsufficientHistory++;
                    continue;
                }

                BulkDealContext context = bulkDealContextReader.findContext(
                    deal.symbol(), deal.dealDate(), deal.buySell(), deal.clientNameNormalized()
                );

                DealMaterialityInput input = new DealMaterialityInput(
                    deal.id(), deal.symbol(), deal.dealDate(), deal.dealValue(), deal.buySell(),
                    adtv20.get(),
                    context.sameSideClientDealCount20CalendarDays(), context.distinctSameSideClients20CalendarDays(),
                    context.distinctBuyers20CalendarDays(), context.distinctSellers20CalendarDays(),
                    context.reportedBuyValue20CalendarDays(), context.reportedSellValue20CalendarDays()
                );

                writer.write(engine.calculate(input, rules));
                scored++;
            } catch (Exception e) {
                failed++;
                log.error("Failed to score deal materiality for {} (deal {}): {}", deal.symbol(), deal.id(), e.getMessage(), e);
            }
        }

        log.info(
            "Deal materiality scoring complete: {} scored, {} skipped (insufficient price history), {} failed (of {} pending)",
            scored, skippedInsufficientHistory, failed, pending.size()
        );
    }
}
