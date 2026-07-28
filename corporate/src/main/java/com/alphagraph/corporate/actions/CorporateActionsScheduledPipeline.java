package com.alphagraph.corporate.actions;

import com.alphagraph.common.etl.PipelineDefinition;
import com.alphagraph.common.etl.PipelineRunner;
import com.alphagraph.common.etl.RequiredFieldsValidator;
import com.alphagraph.common.etl.ScheduledPipeline;
import com.alphagraph.common.etl.SourceConfig;
import com.alphagraph.common.etl.Validator;
import com.alphagraph.common.quality.DataQualitySpec;
import com.alphagraph.corporate.api.CorporateAction;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Module 1.4: registers the corporate actions pipeline for the scheduler to discover.
 *
 * Sample data sourcing note: all 6 resolvable rows are real FY25 final dividends (ex-date,
 * record-date, and per-share amount looked up from public dividend-tracking sources at the time
 * this was written) for RELIANCE/TCS/INFY/HDFCBANK/ICICIBANK/ESCORTS. Unlike Modules 1.2/1.3,
 * there are no BEML/ACE estimate rows here - estimating the *magnitude* of a metric every company
 * reports (like quarterly PAT) is one thing, but guessing *whether a specific corporate event
 * happened* would be inventing a fact rather than approximating a real one, so this sample only
 * includes actions that were actually confirmed. No BONUS/SPLIT/RIGHTS/BUYBACK events were found
 * for these companies in the current period, so only DIVIDEND rows appear - the schema still
 * models the other types via action_type and the nullable ratio/price columns, they're just not
 * exercised by this sample. This is a stand-in for a real automated source (see the package-info
 * for why one doesn't cleanly exist yet).
 *
 * Depends on the concrete CorporateActionsCollector, not the Collector interface - unlike market
 * (which needs interface-based swapping between the bundled sample and the real HTTP fetch),
 * there's only one corporate collector so far. Market's own Collector<List<String>> beans
 * (BhavdataCollector/HttpBhavdataCollector) would otherwise collide with this one at the interface
 * level (confirmed the hard way in Module 1.2 - NoUniqueBeanDefinitionException).
 */
@Component
public class CorporateActionsScheduledPipeline implements ScheduledPipeline {

    private static final String CRON_6PM_IST = "0 0 18 * * *";

    private final CorporateActionsCollector collector;
    private final CorporateActionsParser parser;
    private final CorporateActionsNormalizer normalizer;
    private final CorporateActionsLoader loader;

    public CorporateActionsScheduledPipeline(
        CorporateActionsCollector collector, CorporateActionsParser parser,
        CorporateActionsNormalizer normalizer, CorporateActionsLoader loader
    ) {
        this.collector = collector;
        this.parser = parser;
        this.normalizer = normalizer;
        this.loader = loader;
    }

    @Override
    public String name() {
        return "corporate-actions";
    }

    @Override
    public void run(PipelineRunner runner) {
        SourceConfig sourceConfig = new SourceConfig(
            name(), "corporate", Map.of("resourcePath", "corporate-data/sample-corporate-actions.csv")
        );

        Map<String, Function<RawCorporateActionRow, ?>> requiredFields = Map.of(
            "symbol", RawCorporateActionRow::symbol, "actionType", RawCorporateActionRow::actionType,
            "exDate", RawCorporateActionRow::exDate
        );

        Validator<RawCorporateActionRow> validator = new RequiredFieldsValidator<>(requiredFields);

        PipelineDefinition<List<String>, RawCorporateActionRow, CorporateAction> definition = new PipelineDefinition<>(
            sourceConfig, collector, parser, validator, normalizer, loader
        );

        Map<String, Function<RawCorporateActionRow, ?>> expectedFields = new HashMap<>(requiredFields);
        expectedFields.put("recordDate", RawCorporateActionRow::recordDate);
        expectedFields.put("announcementDate", RawCorporateActionRow::announcementDate);
        expectedFields.put("dividendAmount", RawCorporateActionRow::dividendAmount);
        expectedFields.put("ratioNumerator", RawCorporateActionRow::ratioNumerator);
        expectedFields.put("ratioDenominator", RawCorporateActionRow::ratioDenominator);
        expectedFields.put("price", RawCorporateActionRow::price);
        DataQualitySpec<RawCorporateActionRow> qualitySpec = new DataQualitySpec<>(
            expectedFields, requiredFields.keySet(), RawCorporateActionRow::symbol
        );

        runner.run(definition, qualitySpec, CRON_6PM_IST);
    }
}
