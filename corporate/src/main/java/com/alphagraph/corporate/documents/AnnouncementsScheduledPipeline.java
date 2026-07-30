package com.alphagraph.corporate.documents;

import com.alphagraph.common.etl.Collector;
import com.alphagraph.common.etl.PipelineDefinition;
import com.alphagraph.common.etl.PipelineRunner;
import com.alphagraph.common.etl.RequiredFieldsValidator;
import com.alphagraph.common.etl.ScheduledPipeline;
import com.alphagraph.common.etl.SourceConfig;
import com.alphagraph.common.etl.Validator;
import com.alphagraph.common.quality.DataQualitySpec;
import com.alphagraph.corporate.api.CorporateDocument;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Module 2.1: registers the Exchange Announcements collection pipeline for the scheduler to
 * discover - the first stage of the Document Pipeline (Download). Runs alongside every other
 * Phase 1 raw-ingestion pipeline at 6PM IST; {@link DocumentProcessingOrchestrator} (OCR/Parse ->
 * Extract -> Chunk -> Entity Extraction -> Embeddings) runs later on its own schedule once
 * documents exist to process.
 *
 * Depends on the generic {@code Collector<String>} interface (not a concrete type) since this
 * genuinely needs runtime swapping between the bundled real snapshot
 * ({@link AnnouncementsCollector}, local/test) and the live NSE fetch
 * ({@link HttpAnnouncementsCollector}, docker/prod) - same reason market's daily pipeline and
 * ownership's bulk/block deals pipeline do. {@code @Qualifier("corporate-announcements")} is
 * load-bearing: without it, any other module's {@code Collector<String>} bean is an equally valid
 * candidate for this exact generic type.
 */
@Component
public class AnnouncementsScheduledPipeline implements ScheduledPipeline {

    private static final String CRON_6PM_IST = "0 0 18 * * *";

    private final Collector<String> collector;
    private final AnnouncementsParser parser;
    private final AnnouncementsNormalizer normalizer;
    private final AnnouncementsLoader loader;

    public AnnouncementsScheduledPipeline(
        @Qualifier("corporate-announcements") Collector<String> collector,
        AnnouncementsParser parser, AnnouncementsNormalizer normalizer, AnnouncementsLoader loader
    ) {
        this.collector = collector;
        this.parser = parser;
        this.normalizer = normalizer;
        this.loader = loader;
    }

    @Override
    public String name() {
        return "corporate-exchange-announcements";
    }

    @Override
    public void run(PipelineRunner runner) {
        SourceConfig sourceConfig = new SourceConfig(
            name(), "corporate", Map.of("resourcePath", "corporate-data/sample-announcements.json")
        );

        Map<String, Function<RawAnnouncementRow, ?>> requiredFields = Map.of(
            "symbol", RawAnnouncementRow::symbol, "category", RawAnnouncementRow::category,
            "pdfUrl", RawAnnouncementRow::pdfUrl, "announcedAt", RawAnnouncementRow::announcedAt,
            "externalId", RawAnnouncementRow::externalId
        );

        Validator<RawAnnouncementRow> validator = new RequiredFieldsValidator<>(requiredFields);

        PipelineDefinition<String, RawAnnouncementRow, CorporateDocument> definition = new PipelineDefinition<>(
            sourceConfig, collector, parser, validator, normalizer, loader
        );

        Map<String, Function<RawAnnouncementRow, ?>> expectedFields = new HashMap<>(requiredFields);
        expectedFields.put("title", RawAnnouncementRow::title);
        expectedFields.put("isin", RawAnnouncementRow::isin);
        DataQualitySpec<RawAnnouncementRow> qualitySpec = new DataQualitySpec<>(
            expectedFields, requiredFields.keySet(), RawAnnouncementRow::symbol
        );

        runner.run(definition, qualitySpec, CRON_6PM_IST);
    }
}
