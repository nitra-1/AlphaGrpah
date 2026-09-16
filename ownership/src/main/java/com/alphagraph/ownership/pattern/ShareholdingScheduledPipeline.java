package com.alphagraph.ownership.pattern;

import com.alphagraph.common.etl.Collector;
import com.alphagraph.common.etl.PipelineDefinition;
import com.alphagraph.common.etl.PipelineRunner;
import com.alphagraph.common.etl.RequiredFieldsValidator;
import com.alphagraph.common.etl.ScheduledPipeline;
import com.alphagraph.common.etl.SourceConfig;
import com.alphagraph.common.etl.Validator;
import com.alphagraph.common.quality.DataQualitySpec;
import com.alphagraph.ownership.api.ShareholdingPattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Module 1.2 (now live): registers the shareholding pattern pipeline for the scheduler to
 * discover.
 *
 * <p>Depends on the generic {@code Collector<String>} interface, not a concrete type - now that a
 * live source exists ({@link HttpShareholdingCollector}) alongside the bundled sample
 * ({@link ShareholdingCollector}), this needs the same runtime swapping every other dual-collector
 * module uses. {@code @Qualifier("shareholding-pattern")} is load-bearing: without it, any other
 * module's {@code Collector<String>} bean is an equally valid candidate for the same generic type.
 *
 * <p>{@code fiiPct}/{@code diiPct} are no longer required input fields - the live feed doesn't
 * carry them at all (they arrive later via XBRL enrichment); only {@code symbol}/{@code periodEnd}/
 * {@code promoterPct} are guaranteed present on every row.
 */
@Component
public class ShareholdingScheduledPipeline implements ScheduledPipeline {

    private static final String CRON_6PM_IST = "0 0 18 * * *";

    private final Collector<String> collector;
    private final ShareholdingParser parser;
    private final ShareholdingNormalizer normalizer;
    private final ShareholdingLoader loader;

    public ShareholdingScheduledPipeline(
        @Qualifier("shareholding-pattern") Collector<String> collector, ShareholdingParser parser,
        ShareholdingNormalizer normalizer, ShareholdingLoader loader
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
            name(), "ownership", Map.of("resourcePath", "ownership-data/sample-shareholding.json")
        );

        Map<String, Function<RawShareholdingRow, ?>> requiredFields = Map.of(
            "symbol", RawShareholdingRow::symbol, "periodEnd", RawShareholdingRow::periodEnd,
            "promoterPct", RawShareholdingRow::promoterPct
        );

        Validator<RawShareholdingRow> validator = new RequiredFieldsValidator<>(requiredFields);

        PipelineDefinition<String, RawShareholdingRow, ShareholdingPattern> definition = new PipelineDefinition<>(
            sourceConfig, collector, parser, validator, normalizer, loader
        );

        Map<String, Function<RawShareholdingRow, ?>> expectedFields = new HashMap<>(requiredFields);
        expectedFields.put("fiiPct", RawShareholdingRow::fiiPct);
        expectedFields.put("diiPct", RawShareholdingRow::diiPct);
        expectedFields.put("mfPct", RawShareholdingRow::mfPct);
        expectedFields.put("publicPct", RawShareholdingRow::publicPct);
        DataQualitySpec<RawShareholdingRow> qualitySpec = new DataQualitySpec<>(
            expectedFields, requiredFields.keySet(), RawShareholdingRow::symbol
        );

        runner.run(definition, qualitySpec, CRON_6PM_IST);
    }
}
