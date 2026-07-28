package com.alphagraph.intelligence.technical;

import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.market.api.DailyPrice;
import com.alphagraph.market.api.DailyPriceReader;
import com.alphagraph.technical.api.DailyBar;
import com.alphagraph.technical.api.TechnicalEngineInput;
import com.alphagraph.technical.api.TechnicalScore;
import com.alphagraph.technical.engine.RuleSetLoader;
import com.alphagraph.technical.engine.TechnicalEngine;
import com.alphagraph.technical.engine.TechnicalScoreWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Runs the Technical Engine over every instrument with at least one day of market history.
 * Reads through {@link DailyPriceReader} (market's published API, never market's internal
 * tables/classes), maps to technical's own {@link DailyBar} shape, and persists via technical's
 * own {@link TechnicalScoreWriter} - this class does no direct SQL of its own.
 */
@Component
public class TechnicalAnalysisOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(TechnicalAnalysisOrchestrator.class);

    private final DailyPriceReader dailyPriceReader;
    private final RuleSetLoader ruleSetLoader;
    private final TechnicalEngine technicalEngine;
    private final TechnicalScoreWriter technicalScoreWriter;

    public TechnicalAnalysisOrchestrator(
        DailyPriceReader dailyPriceReader, RuleSetLoader ruleSetLoader,
        TechnicalEngine technicalEngine, TechnicalScoreWriter technicalScoreWriter
    ) {
        this.dailyPriceReader = dailyPriceReader;
        this.ruleSetLoader = ruleSetLoader;
        this.technicalEngine = technicalEngine;
        this.technicalScoreWriter = technicalScoreWriter;
    }

    public void runForAllInstruments() {
        RuleSet rules = ruleSetLoader.loadActiveTechnicalRules();
        List<UUID> instrumentIds = dailyPriceReader.instrumentIdsWithHistory();

        int computed = 0;
        for (UUID instrumentId : instrumentIds) {
            List<DailyPrice> history = dailyPriceReader.findHistory(instrumentId);
            if (history.isEmpty()) {
                continue;
            }

            List<DailyBar> bars = history.stream()
                .map(p -> new DailyBar(p.tradeDate(), p.open(), p.high(), p.low(), p.close(), p.volume(), p.deliveryPercentage()))
                .toList();

            TechnicalEngineInput input = new TechnicalEngineInput(instrumentId, history.get(0).symbol(), bars);
            TechnicalScore score = technicalEngine.calculate(input, rules);
            technicalScoreWriter.write(score);
            computed++;
        }

        log.info("Technical analysis computed for {} instrument(s), rule set version {}", computed, rules.version());
    }
}
