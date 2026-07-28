package com.alphagraph.financial.results;

import com.alphagraph.common.etl.PipelineDefinition;
import com.alphagraph.common.etl.PipelineRunner;
import com.alphagraph.common.etl.RequiredFieldsValidator;
import com.alphagraph.common.etl.ScheduledPipeline;
import com.alphagraph.common.etl.SourceConfig;
import com.alphagraph.common.etl.Validator;
import com.alphagraph.common.quality.DataQualitySpec;
import com.alphagraph.financial.api.FinancialResult;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Module 1.3: registers the financial results (fundamentals) pipeline for the scheduler to
 * discover.
 *
 * Sample data sourcing note: RELIANCE/TCS/INFY/HDFCBANK/ICICIBANK Q4 FY25 figures were looked up
 * from public quarterly-results reporting at the time this was written (sales, PAT, EPS where
 * disclosed; ROE/ROCE/margins where a source gave them directly - net_margin for RELIANCE/INFY is
 * derived from PAT/sales rather than separately reported). ESCORTS' sales/PAT are from its real
 * Q4 FY25 result; its other fields weren't confidently isolated at the single-quarter level and
 * are left null rather than guessed. BEML/ACE figures are representative estimates from general
 * knowledge, not independently verified. All of it is a stand-in for a real automated source (see
 * the package-info for why one doesn't cleanly exist yet).
 *
 * Depends on the concrete FinancialResultsCollector, not the Collector interface - unlike market
 * (which needs interface-based swapping between the bundled sample and the real HTTP fetch),
 * there's only one financial collector so far. Market's own Collector<List<String>> beans
 * (BhavdataCollector/HttpBhavdataCollector) would otherwise collide with this one at the interface
 * level (confirmed the hard way in Module 1.2 - NoUniqueBeanDefinitionException).
 */
@Component
public class FinancialResultsScheduledPipeline implements ScheduledPipeline {

    private static final String CRON_6PM_IST = "0 0 18 * * *";

    private final FinancialResultsCollector collector;
    private final FinancialResultsParser parser;
    private final FinancialResultsNormalizer normalizer;
    private final FinancialResultsLoader loader;

    public FinancialResultsScheduledPipeline(
        FinancialResultsCollector collector, FinancialResultsParser parser,
        FinancialResultsNormalizer normalizer, FinancialResultsLoader loader
    ) {
        this.collector = collector;
        this.parser = parser;
        this.normalizer = normalizer;
        this.loader = loader;
    }

    @Override
    public String name() {
        return "financial-quarterly-results";
    }

    @Override
    public void run(PipelineRunner runner) {
        SourceConfig sourceConfig = new SourceConfig(
            name(), "financial", Map.of("resourcePath", "financial-data/sample-financial-results.csv")
        );

        Map<String, Function<RawFinancialResultRow, ?>> requiredFields = Map.of(
            "symbol", RawFinancialResultRow::symbol, "periodEnd", RawFinancialResultRow::periodEnd,
            "periodType", RawFinancialResultRow::periodType, "sales", RawFinancialResultRow::sales,
            "pat", RawFinancialResultRow::pat
        );

        Validator<RawFinancialResultRow> validator = new RequiredFieldsValidator<>(requiredFields);

        PipelineDefinition<List<String>, RawFinancialResultRow, FinancialResult> definition = new PipelineDefinition<>(
            sourceConfig, collector, parser, validator, normalizer, loader
        );

        Map<String, Function<RawFinancialResultRow, ?>> expectedFields = new HashMap<>(requiredFields);
        expectedFields.put("eps", RawFinancialResultRow::eps);
        expectedFields.put("roePct", RawFinancialResultRow::roePct);
        expectedFields.put("rocePct", RawFinancialResultRow::rocePct);
        expectedFields.put("operatingMarginPct", RawFinancialResultRow::operatingMarginPct);
        expectedFields.put("netMarginPct", RawFinancialResultRow::netMarginPct);
        expectedFields.put("cashFlowFromOps", RawFinancialResultRow::cashFlowFromOps);
        DataQualitySpec<RawFinancialResultRow> qualitySpec = new DataQualitySpec<>(
            expectedFields, requiredFields.keySet(), RawFinancialResultRow::symbol
        );

        runner.run(definition, qualitySpec, CRON_6PM_IST);
    }
}
