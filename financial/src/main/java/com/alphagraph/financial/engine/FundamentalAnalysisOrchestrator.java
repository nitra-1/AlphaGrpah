package com.alphagraph.financial.engine;

import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.financial.api.FinancialResult;
import com.alphagraph.financial.api.FundamentalEngineInput;
import com.alphagraph.financial.api.FundamentalScore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Runs the Fundamental Engine over every instrument with at least one financial_results period.
 * Entirely within the financial module - no intelligence-bridging needed, unlike Module 1.5's
 * TechnicalAnalysisOrchestrator, since the data this reads is financial's own.
 */
@Component
public class FundamentalAnalysisOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(FundamentalAnalysisOrchestrator.class);

    private final FinancialResultReader financialResultReader;
    private final FundamentalRuleSetLoader ruleSetLoader;
    private final FundamentalEngine fundamentalEngine;
    private final FundamentalScoreWriter fundamentalScoreWriter;

    public FundamentalAnalysisOrchestrator(
        FinancialResultReader financialResultReader, FundamentalRuleSetLoader ruleSetLoader,
        FundamentalEngine fundamentalEngine, FundamentalScoreWriter fundamentalScoreWriter
    ) {
        this.financialResultReader = financialResultReader;
        this.ruleSetLoader = ruleSetLoader;
        this.fundamentalEngine = fundamentalEngine;
        this.fundamentalScoreWriter = fundamentalScoreWriter;
    }

    public void runForAllInstruments() {
        RuleSet rules = ruleSetLoader.loadActiveFundamentalRules();
        List<UUID> instrumentIds = financialResultReader.instrumentIdsWithResults();

        int computed = 0;
        for (UUID instrumentId : instrumentIds) {
            List<FinancialResult> periods = financialResultReader.findPeriods(instrumentId);
            if (periods.isEmpty()) {
                continue;
            }

            FundamentalEngineInput input = new FundamentalEngineInput(instrumentId, periods.get(0).symbol(), periods);
            FundamentalScore score = fundamentalEngine.calculate(input, rules);
            fundamentalScoreWriter.write(score);
            computed++;
        }

        log.info("Fundamental analysis computed for {} instrument(s), rule set version {}", computed, rules.version());
    }
}
