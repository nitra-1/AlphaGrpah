package com.alphagraph.reference.securitymaster;

import com.alphagraph.common.etl.Collector;
import com.alphagraph.common.etl.PipelineDefinition;
import com.alphagraph.common.etl.PipelineRunner;
import com.alphagraph.common.etl.RequiredFieldsValidator;
import com.alphagraph.common.etl.ScheduledPipeline;
import com.alphagraph.common.etl.SourceConfig;
import com.alphagraph.common.etl.Validator;
import com.alphagraph.common.quality.DataQualitySpec;
import com.alphagraph.reference.api.SecurityMasterEntry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Registers the security master refresh with the scheduler's registry - runs alongside every
 * other daily pipeline at 18:00 IST (docs/002_Engine_Architecture.md §6). Running daily is more
 * than the ~2,400-row list actually needs (NSE adds/removes listings occasionally, not daily),
 * but the registry only supports one shared cron for every registered pipeline - the marginal
 * cost of one extra low-risk fetch in the existing daily batch isn't worth a second scheduling
 * mechanism just to run this one less often.
 */
@Component
public class SecurityMasterScheduledPipeline implements ScheduledPipeline {

    private static final String CRON_6PM_IST = "0 0 18 * * *";

    private final Collector<java.util.List<String>> collector;
    private final SecurityMasterParser parser;
    private final SecurityMasterNormalizer normalizer;
    private final SecurityMasterLoader loader;

    public SecurityMasterScheduledPipeline(
        @Qualifier("security-master") Collector<java.util.List<String>> collector,
        SecurityMasterParser parser, SecurityMasterNormalizer normalizer, SecurityMasterLoader loader
    ) {
        this.collector = collector;
        this.parser = parser;
        this.normalizer = normalizer;
        this.loader = loader;
    }

    @Override
    public String name() {
        return "reference-security-master";
    }

    @Override
    public void run(PipelineRunner runner) {
        SourceConfig sourceConfig = new SourceConfig(name(), "reference", Map.of());

        Map<String, Function<RawSecurityMasterRow, ?>> requiredFields = Map.of(
            "symbol", RawSecurityMasterRow::symbol, "companyName", RawSecurityMasterRow::companyName,
            "series", RawSecurityMasterRow::series, "isin", RawSecurityMasterRow::isin
        );

        Validator<RawSecurityMasterRow> validator = new RequiredFieldsValidator<>(requiredFields);

        PipelineDefinition<java.util.List<String>, RawSecurityMasterRow, SecurityMasterEntry> definition =
            new PipelineDefinition<>(sourceConfig, collector, parser, validator, normalizer, loader);

        Map<String, Function<RawSecurityMasterRow, ?>> expectedFields = new HashMap<>(requiredFields);
        expectedFields.put("listingDate", RawSecurityMasterRow::listingDate);
        expectedFields.put("faceValue", RawSecurityMasterRow::faceValue);
        DataQualitySpec<RawSecurityMasterRow> qualitySpec = new DataQualitySpec<>(
            expectedFields, requiredFields.keySet(), RawSecurityMasterRow::symbol
        );

        runner.run(definition, qualitySpec, CRON_6PM_IST);
    }
}
