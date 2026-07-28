package com.alphagraph.ownership.pattern;

import com.alphagraph.common.etl.PipelineDefinition;
import com.alphagraph.common.etl.PipelineRunner;
import com.alphagraph.common.etl.RequiredFieldsValidator;
import com.alphagraph.common.etl.ScheduledPipeline;
import com.alphagraph.common.etl.SourceConfig;
import com.alphagraph.common.etl.Validator;
import com.alphagraph.common.quality.DataQualitySpec;
import com.alphagraph.ownership.api.ShareholdingPattern;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Module 1.2: registers the shareholding pattern pipeline for the scheduler to discover.
 *
 * Sample data sourcing note: RELIANCE/TCS/INFY/HDFCBANK/ICICIBANK/ESCORTS figures were looked
 * up from public shareholding-pattern aggregator sites at the time this was written and are
 * reasonably current; BEML/ACE figures are representative estimates from general knowledge, not
 * independently verified in that same research pass. All of it is a stand-in for a real
 * automated source (see the package-info for why one doesn't cleanly exist yet).
 *
 * Depends on the concrete ShareholdingCollector, not the Collector interface — unlike market
 * (which needs interface-based swapping between the bundled sample and the real HTTP fetch),
 * there's only one ownership collector so far. Market's own Collector<List<String>> beans
 * (BhavdataCollector/HttpBhavdataCollector) would otherwise collide with this one at the
 * interface level (confirmed the hard way - NoUniqueBeanDefinitionException).
 */
@Component
public class ShareholdingScheduledPipeline implements ScheduledPipeline {

    private static final String CRON_6PM_IST = "0 0 18 * * *";

    private final ShareholdingCollector collector;
    private final ShareholdingParser parser;
    private final ShareholdingNormalizer normalizer;
    private final ShareholdingLoader loader;

    public ShareholdingScheduledPipeline(
        ShareholdingCollector collector, ShareholdingParser parser, ShareholdingNormalizer normalizer, ShareholdingLoader loader
    ) {
        this.collector = collector;
        this.parser = parser;
        this.normalizer = normalizer;
        this.loader = loader;
    }

    @Override
    public String name() {
        return "ownership-shareholding-pattern";
    }

    @Override
    public void run(PipelineRunner runner) {
        SourceConfig sourceConfig = new SourceConfig(
            name(), "ownership", Map.of("resourcePath", "ownership-data/sample-shareholding.csv")
        );

        Map<String, Function<RawShareholdingRow, ?>> requiredFields = Map.of(
            "symbol", RawShareholdingRow::symbol, "periodEnd", RawShareholdingRow::periodEnd,
            "promoterPct", RawShareholdingRow::promoterPct, "fiiPct", RawShareholdingRow::fiiPct,
            "diiPct", RawShareholdingRow::diiPct
        );

        Validator<RawShareholdingRow> validator = new RequiredFieldsValidator<>(requiredFields);

        PipelineDefinition<List<String>, RawShareholdingRow, ShareholdingPattern> definition = new PipelineDefinition<>(
            sourceConfig, collector, parser, validator, normalizer, loader
        );

        Map<String, Function<RawShareholdingRow, ?>> expectedFields = new HashMap<>(requiredFields);
        expectedFields.put("mfPct", RawShareholdingRow::mfPct);
        expectedFields.put("publicPct", RawShareholdingRow::publicPct);
        DataQualitySpec<RawShareholdingRow> qualitySpec = new DataQualitySpec<>(
            expectedFields, requiredFields.keySet(), RawShareholdingRow::symbol
        );

        runner.run(definition, qualitySpec, CRON_6PM_IST);
    }
}
