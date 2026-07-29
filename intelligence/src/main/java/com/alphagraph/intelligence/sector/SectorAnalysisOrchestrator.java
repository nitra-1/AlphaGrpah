package com.alphagraph.intelligence.sector;

import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.market.api.DailyPrice;
import com.alphagraph.market.api.DailyPriceReader;
import com.alphagraph.sector.api.SectorBar;
import com.alphagraph.sector.api.SectorConstituentInput;
import com.alphagraph.sector.api.SectorEngineInput;
import com.alphagraph.sector.api.SectorScore;
import com.alphagraph.sector.engine.SectorEngine;
import com.alphagraph.sector.engine.SectorRuleSetLoader;
import com.alphagraph.sector.engine.SectorScoreWriter;
import com.alphagraph.sector.mapping.SectorMappingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs the Sector Engine over every sector that has at least one instrument assigned to it.
 * Reads market's price/volume history through {@link DailyPriceReader} (market's published API,
 * never market's internal tables/classes) and sector's own mapping through
 * {@link SectorMappingReader}, then persists via sector's own {@link SectorScoreWriter} - this
 * class does no direct SQL of its own.
 */
@Component
public class SectorAnalysisOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SectorAnalysisOrchestrator.class);

    private final SectorMappingReader sectorMappingReader;
    private final DailyPriceReader dailyPriceReader;
    private final SectorRuleSetLoader ruleSetLoader;
    private final SectorEngine sectorEngine;
    private final SectorScoreWriter sectorScoreWriter;

    public SectorAnalysisOrchestrator(
        SectorMappingReader sectorMappingReader, DailyPriceReader dailyPriceReader,
        SectorRuleSetLoader ruleSetLoader, SectorEngine sectorEngine, SectorScoreWriter sectorScoreWriter
    ) {
        this.sectorMappingReader = sectorMappingReader;
        this.dailyPriceReader = dailyPriceReader;
        this.ruleSetLoader = ruleSetLoader;
        this.sectorEngine = sectorEngine;
        this.sectorScoreWriter = sectorScoreWriter;
    }

    public void runForAllSectors() {
        RuleSet rules = ruleSetLoader.loadActiveSectorRules();

        List<UUID> allInstrumentIds = sectorMappingReader.allTrackedInstrumentIds();
        List<SectorConstituentInput> allTrackedInstruments = allInstrumentIds.stream()
            .map(this::toConstituentInput)
            .toList();

        List<Map<String, Object>> sectors = sectorMappingReader.sectorsWithConstituents();
        int computed = 0;
        for (Map<String, Object> sectorRow : sectors) {
            UUID sectorId = (UUID) sectorRow.get("sector_id");
            String sectorName = (String) sectorRow.get("sector_name");

            List<UUID> constituentIds = sectorMappingReader.constituentInstrumentIds(sectorId);
            if (constituentIds.isEmpty()) {
                continue;
            }

            List<SectorConstituentInput> constituents = constituentIds.stream()
                .map(this::toConstituentInput)
                .toList();

            SectorEngineInput input = new SectorEngineInput(sectorId, sectorName, constituents, allTrackedInstruments);
            SectorScore score = sectorEngine.calculate(input, rules);
            sectorScoreWriter.write(score);
            computed++;
        }

        log.info("Sector analysis computed for {} sector(s), rule set version {}", computed, rules.version());
    }

    private SectorConstituentInput toConstituentInput(UUID instrumentId) {
        List<DailyPrice> history = dailyPriceReader.findHistory(instrumentId);
        String symbol = history.isEmpty() ? instrumentId.toString() : history.get(0).symbol();
        List<SectorBar> bars = history.stream()
            .map(p -> new SectorBar(p.tradeDate(), p.close(), p.volume()))
            .toList();
        return new SectorConstituentInput(instrumentId, symbol, bars);
    }
}
