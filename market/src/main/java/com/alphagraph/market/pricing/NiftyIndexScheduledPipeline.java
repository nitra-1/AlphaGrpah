package com.alphagraph.market.pricing;

import com.alphagraph.common.etl.Collector;
import com.alphagraph.common.etl.PipelineDefinition;
import com.alphagraph.common.etl.PipelineRunner;
import com.alphagraph.common.etl.RequiredFieldsValidator;
import com.alphagraph.common.etl.ScheduledPipeline;
import com.alphagraph.common.etl.SourceConfig;
import com.alphagraph.common.etl.Validator;
import com.alphagraph.common.quality.DataQualitySpec;
import com.alphagraph.market.api.DailyPrice;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Outcome Evidence Enrichment: registers the daily NIFTY 50 index-close pipeline, feeding
 * {@code market.daily_prices} for the market-benchmark instrument the same way
 * {@link MarketDataScheduledPipeline} feeds every tracked equity - reusing {@link DailyPriceLoader}
 * and {@link InstrumentLookup} directly, since a resolved {@link DailyPrice} is a resolved
 * {@link DailyPrice} regardless of whether it came from an equity or an index row.
 *
 * <p>Runs 5 minutes after the equity bhavdata pipeline (18:05 IST vs 18:00 IST) - independent HTTP
 * fetches against different NSE endpoints, staggered only to avoid firing two outbound requests in
 * the exact same instant, not because either depends on the other's output.
 */
@Component
public class NiftyIndexScheduledPipeline implements ScheduledPipeline {

    private static final String CRON_1805PM_IST = "0 5 18 * * *";

    private final Collector<List<String>> collector;
    private final IndexBhavdataParser parser;
    private final IndexBhavdataNormalizer normalizer;
    private final DailyPriceLoader loader;

    public NiftyIndexScheduledPipeline(
        @Qualifier("marketIndex") Collector<List<String>> collector,
        IndexBhavdataParser parser, IndexBhavdataNormalizer normalizer, DailyPriceLoader loader
    ) {
        this.collector = collector;
        this.parser = parser;
        this.normalizer = normalizer;
        this.loader = loader;
    }

    @Override
    public String name() {
        return "nifty50-index";
    }

    @Override
    public void run(PipelineRunner runner) {
        SourceConfig sourceConfig = new SourceConfig(name(), "market");

        Map<String, Function<RawIndexRow, ?>> requiredFields = Map.of(
            "indexName", RawIndexRow::indexName, "tradeDate", RawIndexRow::tradeDate,
            "open", RawIndexRow::open, "high", RawIndexRow::high, "low", RawIndexRow::low,
            "close", RawIndexRow::close, "volume", RawIndexRow::volume
        );

        Validator<RawIndexRow> validator = new RequiredFieldsValidator<>(requiredFields);

        PipelineDefinition<List<String>, RawIndexRow, DailyPrice> definition = new PipelineDefinition<>(
            sourceConfig, collector, parser, validator, normalizer, loader
        );

        DataQualitySpec<RawIndexRow> qualitySpec = new DataQualitySpec<>(requiredFields, requiredFields.keySet(), RawIndexRow::indexName);

        runner.run(definition, qualitySpec, CRON_1805PM_IST);
    }
}
