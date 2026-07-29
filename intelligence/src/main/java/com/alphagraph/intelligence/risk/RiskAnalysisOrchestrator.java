package com.alphagraph.intelligence.risk;

import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.financial.api.FinancialResult;
import com.alphagraph.financial.api.FundamentalScore;
import com.alphagraph.financial.engine.FinancialResultReader;
import com.alphagraph.financial.engine.FundamentalScoreReader;
import com.alphagraph.market.api.DailyPrice;
import com.alphagraph.market.api.DailyPriceReader;
import com.alphagraph.ownership.api.InstitutionalScore;
import com.alphagraph.ownership.engine.InstitutionalScoreReader;
import com.alphagraph.risk.api.RiskEngineInput;
import com.alphagraph.risk.api.RiskScore;
import com.alphagraph.risk.engine.RiskEngine;
import com.alphagraph.risk.engine.RiskRuleSetLoader;
import com.alphagraph.risk.engine.RiskScoreWriter;
import com.alphagraph.technical.api.TechnicalScore;
import com.alphagraph.technical.engine.TechnicalScoreReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs the Risk Engine over every instrument with at least one day of market history - the same
 * driving set {@code intelligence.technical}'s orchestrator uses (Module 1.5), since Valuation
 * Risk needs a latest close and every other category's upstream engine already requires price
 * history to have run in the first place.
 *
 * <p>The most cross-cutting orchestrator yet: it bridges FOUR domains rather than one or two
 * (Technical/Fundamental/Institutional's own already-computed scores, Modules 1.5-1.7, plus
 * financial's raw EPS/PAT/TotalEquity for the PE/PB derivation) - reaching into each domain
 * module's {@code .engine} package directly, matching the established practice from Module 1.7's
 * {@code InstitutionalAnalysisOrchestrator} (which already reached past {@code ownership.api}
 * into {@code ownership.engine}), not a new published-interface layer.
 *
 * <p>Only market price history is mandatory. Every other domain's read is {@link Optional} and
 * missing data degrades that one category's rules to "no signal" (0 contribution) rather than
 * skipping the instrument entirely - the same null-tolerant design {@link RiskEngine} already
 * has for every other Phase 1 engine's inputs.
 */
@Component
public class RiskAnalysisOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(RiskAnalysisOrchestrator.class);

    private final DailyPriceReader dailyPriceReader;
    private final FinancialResultReader financialResultReader;
    private final FundamentalScoreReader fundamentalScoreReader;
    private final TechnicalScoreReader technicalScoreReader;
    private final InstitutionalScoreReader institutionalScoreReader;
    private final RiskRuleSetLoader ruleSetLoader;
    private final RiskEngine riskEngine;
    private final RiskScoreWriter riskScoreWriter;

    public RiskAnalysisOrchestrator(
        DailyPriceReader dailyPriceReader, FinancialResultReader financialResultReader,
        FundamentalScoreReader fundamentalScoreReader, TechnicalScoreReader technicalScoreReader,
        InstitutionalScoreReader institutionalScoreReader, RiskRuleSetLoader ruleSetLoader,
        RiskEngine riskEngine, RiskScoreWriter riskScoreWriter
    ) {
        this.dailyPriceReader = dailyPriceReader;
        this.financialResultReader = financialResultReader;
        this.fundamentalScoreReader = fundamentalScoreReader;
        this.technicalScoreReader = technicalScoreReader;
        this.institutionalScoreReader = institutionalScoreReader;
        this.ruleSetLoader = ruleSetLoader;
        this.riskEngine = riskEngine;
        this.riskScoreWriter = riskScoreWriter;
    }

    public void runForAllInstruments() {
        RuleSet rules = ruleSetLoader.loadActiveRiskRules();
        List<UUID> instrumentIds = dailyPriceReader.instrumentIdsWithHistory();

        int computed = 0;
        for (UUID instrumentId : instrumentIds) {
            List<DailyPrice> history = dailyPriceReader.findHistory(instrumentId);
            if (history.isEmpty()) {
                continue;
            }
            DailyPrice latestPrice = history.get(history.size() - 1);

            List<FinancialResult> financialPeriods = financialResultReader.findPeriods(instrumentId);
            FinancialResult latestFinancialResult = financialPeriods.isEmpty()
                ? null
                : financialPeriods.get(financialPeriods.size() - 1);

            Optional<FundamentalScore> fundamentalScore = fundamentalScoreReader.findLatest(instrumentId);
            Optional<TechnicalScore> technicalScore = technicalScoreReader.findLatest(instrumentId);
            Optional<InstitutionalScore> institutionalScore = institutionalScoreReader.findLatest(instrumentId);

            RiskEngineInput input = new RiskEngineInput(
                instrumentId, latestPrice.symbol(), latestPrice.tradeDate(),
                technicalScore.map(t -> t.trend().name()).orElse(null),
                technicalScore.map(t -> t.momentum().name()).orElse(null),
                technicalScore.map(t -> t.volumeState().name()).orElse(null),
                fundamentalScore.map(f -> f.businessGrowth().name()).orElse(null),
                fundamentalScore.map(f -> f.profitability().name()).orElse(null),
                fundamentalScore.map(f -> f.financialQuality().name()).orElse(null),
                fundamentalScore.map(FundamentalScore::debtToEquity).orElse(null),
                fundamentalScore.map(FundamentalScore::revenueGrowthPercentage).orElse(null),
                fundamentalScore.map(FundamentalScore::cashConversionRatio).orElse(null),
                institutionalScore.map(i -> i.promoterStatus().name()).orElse(null),
                institutionalScore.map(i -> i.fiiStatus().name()).orElse(null),
                institutionalScore.map(i -> i.mfStatus().name()).orElse(null),
                institutionalScore.map(i -> i.deliveryStatus().name()).orElse(null),
                latestPrice.close().doubleValue(),
                latestFinancialResult == null || latestFinancialResult.eps() == null ? null : latestFinancialResult.eps().doubleValue(),
                latestFinancialResult == null || latestFinancialResult.pat() == null ? null : latestFinancialResult.pat().doubleValue(),
                latestFinancialResult == null || latestFinancialResult.totalEquity() == null ? null : latestFinancialResult.totalEquity().doubleValue()
            );

            RiskScore score = riskEngine.calculate(input, rules);
            riskScoreWriter.write(score);
            computed++;
        }

        log.info("Risk analysis computed for {} instrument(s), rule set version {}", computed, rules.version());
    }
}
