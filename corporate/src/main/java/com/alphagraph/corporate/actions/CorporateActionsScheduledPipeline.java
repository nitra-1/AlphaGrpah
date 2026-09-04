package com.alphagraph.corporate.actions;

import com.alphagraph.common.etl.Collector;
import com.alphagraph.common.etl.PipelineDefinition;
import com.alphagraph.common.etl.PipelineRunner;
import com.alphagraph.common.etl.RequiredFieldsValidator;
import com.alphagraph.common.etl.ScheduledPipeline;
import com.alphagraph.common.etl.SourceConfig;
import com.alphagraph.common.etl.Validator;
import com.alphagraph.common.quality.DataQualitySpec;
import com.alphagraph.corporate.api.CorporateAction;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Module 1.4 (now live): registers the corporate actions pipeline for the scheduler to discover.
 *
 * <p>Depends on the generic {@code Collector<String>} interface, not a concrete type - now that a
 * live source exists ({@link HttpCorporateActionsCollector}) alongside the bundled sample
 * ({@link CorporateActionsCollector}), this needs the same runtime swapping every other
 * dual-collector module uses (market's daily pipeline, ownership's bulk/block deals, this module's
 * own corporate-announcements pipeline). {@code @Qualifier("corporate-actions")} is load-bearing:
 * without it, any other module's {@code Collector<String>} bean (e.g.
 * {@code corporate.documents}'s announcements collectors) is an equally valid candidate for the
 * same generic type - confirmed the hard way in Module 1.2.
 *
 * <p>{@code actionType} is no longer a required input field - NSE's real feed has no such column,
 * only a free-text {@code subject} that {@link CorporateActionSubjectParser} classifies inside the
 * Normalizer. {@code exDate} stays required (every action needs one); {@code subject} is required
 * too, since without it there's nothing to classify.
 */
@Component
public class CorporateActionsScheduledPipeline implements ScheduledPipeline {

    private static final String CRON_6PM_IST = "0 0 18 * * *";

    private final Collector<String> collector;
    private final CorporateActionsParser parser;
    private final CorporateActionsNormalizer normalizer;
    private final CorporateActionsLoader loader;

    public CorporateActionsScheduledPipeline(
        @Qualifier("corporate-actions") Collector<String> collector, CorporateActionsParser parser,
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
            name(), "corporate", Map.of("resourcePath", "corporate-data/sample-corporate-actions.json")
        );

        Map<String, Function<RawCorporateActionRow, ?>> requiredFields = Map.of(
            "symbol", RawCorporateActionRow::symbol, "subject", RawCorporateActionRow::subject,
            "exDate", RawCorporateActionRow::exDate
        );

        Validator<RawCorporateActionRow> validator = new RequiredFieldsValidator<>(requiredFields);

        PipelineDefinition<String, RawCorporateActionRow, CorporateAction> definition = new PipelineDefinition<>(
            sourceConfig, collector, parser, validator, normalizer, loader
        );

        Map<String, Function<RawCorporateActionRow, ?>> expectedFields = new HashMap<>(requiredFields);
        expectedFields.put("recordDate", RawCorporateActionRow::recordDate);
        expectedFields.put("announcementDate", RawCorporateActionRow::announcementDate);
        DataQualitySpec<RawCorporateActionRow> qualitySpec = new DataQualitySpec<>(
            expectedFields, requiredFields.keySet(), RawCorporateActionRow::symbol
        );

        runner.run(definition, qualitySpec, CRON_6PM_IST);
    }
}
