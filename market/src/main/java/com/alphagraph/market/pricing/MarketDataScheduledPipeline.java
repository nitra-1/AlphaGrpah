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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Module 1.1: registers the daily NSE OHLC/volume/delivery pipeline for the scheduler to
 * discover. Depends on the Collector interface, not a concrete implementation, so the bundled
 * sample (BhavdataCollector, local/test) and the real HTTP fetch (HttpBhavdataCollector,
 * docker/prod) can coexist — exactly one is active per Spring profile.
 *
 * The @Qualifier("market") is load-bearing, not decorative: without it, any other module's
 * Collector<List<String>> bean (e.g. ownership's ShareholdingCollector) is an equally valid
 * candidate for this exact generic type as far as Spring's concerned, and the app fails to
 * start with NoUniqueBeanDefinitionException.
 */
@Component
public class MarketDataScheduledPipeline implements ScheduledPipeline {

    private static final String CRON_6PM_IST = "0 0 18 * * *";

    private final Collector<List<String>> collector;
    private final BhavdataParser parser;
    private final BhavdataNormalizer normalizer;
    private final DailyPriceLoader loader;

    public MarketDataScheduledPipeline(
        @Qualifier("market") Collector<List<String>> collector,
        BhavdataParser parser, BhavdataNormalizer normalizer, DailyPriceLoader loader
    ) {
        this.collector = collector;
        this.parser = parser;
        this.normalizer = normalizer;
        this.loader = loader;
    }

    @Override
    public String name() {
        return "nse-daily-bhavdata";
    }

    @Override
    public void run(PipelineRunner runner) {
        SourceConfig sourceConfig = new SourceConfig(
            name(), "market", Map.of("resourcePath", "market-data/sample-bhavdata.csv")
        );

        Map<String, Function<RawDeliveryRow, ?>> requiredFields = Map.of(
            "symbol", RawDeliveryRow::symbol, "tradeDate", RawDeliveryRow::tradeDate,
            "open", RawDeliveryRow::open, "high", RawDeliveryRow::high, "low", RawDeliveryRow::low,
            "close", RawDeliveryRow::close, "volume", RawDeliveryRow::volume
        );

        Validator<RawDeliveryRow> validator = new RequiredFieldsValidator<>(requiredFields);

        PipelineDefinition<List<String>, RawDeliveryRow, DailyPrice> definition = new PipelineDefinition<>(
            sourceConfig, collector, parser, validator, normalizer, loader
        );

        Map<String, Function<RawDeliveryRow, ?>> expectedFields = new HashMap<>(requiredFields);
        expectedFields.put("deliveryPercentage", RawDeliveryRow::deliveryPercentage);
        DataQualitySpec<RawDeliveryRow> qualitySpec = new DataQualitySpec<>(
            expectedFields, requiredFields.keySet(), RawDeliveryRow::symbol
        );

        runner.run(definition, qualitySpec, CRON_6PM_IST);
    }
}
