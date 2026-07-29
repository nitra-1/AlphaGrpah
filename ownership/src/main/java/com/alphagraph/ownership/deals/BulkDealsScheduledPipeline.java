package com.alphagraph.ownership.deals;

import com.alphagraph.common.etl.Collector;
import com.alphagraph.common.etl.PipelineDefinition;
import com.alphagraph.common.etl.PipelineRunner;
import com.alphagraph.common.etl.RequiredFieldsValidator;
import com.alphagraph.common.etl.ScheduledPipeline;
import com.alphagraph.common.etl.SourceConfig;
import com.alphagraph.common.etl.Validator;
import com.alphagraph.common.quality.DataQualitySpec;
import com.alphagraph.ownership.api.BulkDeal;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Module 1.7: registers the bulk/block deals pipeline for the scheduler to discover.
 *
 * Depends on the generic {@code Collector<List<String>>} interface, not a concrete type - unlike
 * ownership's other pipelines (shareholding, financial's, corporate's), this one genuinely needs
 * runtime swapping between the bundled real snapshot ({@link BulkDealsCollector}, local/test) and
 * the live NSE fetch ({@link HttpBulkDealsCollector}, docker/prod), same reason market's daily
 * pipeline does. {@code @Qualifier("ownership-bulk-deals")} is load-bearing: without it, any other
 * module's {@code Collector<List<String>>} bean (market's, for instance) is an equally valid
 * candidate for this exact generic type - confirmed the hard way in Module 1.2.
 *
 * Sample data note: the bundled CSVs are a real snapshot of NSE's live bulk.csv/block.csv at the
 * time this was written (2026-07-28) - none of our 8 seeded instruments appear in it, which is
 * expected rather than a bug: bulk/block deals concentrate in small/micro-cap stocks whose free
 * float is thin enough for a single trade to cross NSE's reporting threshold (>= 0.5% of shares
 * for bulk, >= 5 lakh shares or INR 5 crore for block) - our large/mid-cap universe rarely
 * generates one. Every row in this run is expected to quarantine as "unknown instrument", which
 * still proves the real fetch -> parse -> validate -> quarantine path end to end.
 */
@Component
public class BulkDealsScheduledPipeline implements ScheduledPipeline {

    private static final String CRON_6PM_IST = "0 0 18 * * *";

    private final Collector<List<String>> collector;
    private final BulkDealsParser parser;
    private final BulkDealsNormalizer normalizer;
    private final BulkDealsLoader loader;

    public BulkDealsScheduledPipeline(
        @Qualifier("ownership-bulk-deals") Collector<List<String>> collector,
        BulkDealsParser parser, BulkDealsNormalizer normalizer, BulkDealsLoader loader
    ) {
        this.collector = collector;
        this.parser = parser;
        this.normalizer = normalizer;
        this.loader = loader;
    }

    @Override
    public String name() {
        return "ownership-bulk-block-deals";
    }

    @Override
    public void run(PipelineRunner runner) {
        SourceConfig sourceConfig = new SourceConfig(
            name(), "ownership", Map.of(
                "bulkResourcePath", "ownership-data/sample-bulk-deals.csv",
                "blockResourcePath", "ownership-data/sample-block-deals.csv"
            )
        );

        Map<String, Function<RawDealRow, ?>> requiredFields = Map.of(
            "dealType", RawDealRow::dealType, "symbol", RawDealRow::symbol, "dealDate", RawDealRow::dealDate,
            "clientName", RawDealRow::clientName, "buySell", RawDealRow::buySell,
            "quantity", RawDealRow::quantity, "price", RawDealRow::price
        );

        Validator<RawDealRow> validator = new RequiredFieldsValidator<>(requiredFields);

        PipelineDefinition<List<String>, RawDealRow, BulkDeal> definition = new PipelineDefinition<>(
            sourceConfig, collector, parser, validator, normalizer, loader
        );

        DataQualitySpec<RawDealRow> qualitySpec = new DataQualitySpec<>(
            requiredFields, requiredFields.keySet(), RawDealRow::symbol
        );

        runner.run(definition, qualitySpec, CRON_6PM_IST);
    }
}
