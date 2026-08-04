package com.alphagraph.corporate.newsfeed;

import com.alphagraph.common.etl.Collector;
import com.alphagraph.common.etl.PipelineDefinition;
import com.alphagraph.common.etl.PipelineRunner;
import com.alphagraph.common.etl.RequiredFieldsValidator;
import com.alphagraph.common.etl.ScheduledPipeline;
import com.alphagraph.common.etl.SourceConfig;
import com.alphagraph.common.etl.Validator;
import com.alphagraph.common.quality.DataQualitySpec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Module 2.6: registers the general-news RSS collection pipeline for the scheduler to discover,
 * at the same 18:00 IST slot as {@code corporate.documents.AnnouncementsScheduledPipeline} and
 * {@code corporate.actions.CorporateActionsScheduledPipeline} - both are "Download"-stage
 * pipelines feeding {@code corporate.documents}, and since {@link NewsFeedLoader} writes straight
 * to PROCESSED (no OCR/chunking wait), news documents collected here are already ready for the
 * 18:30 {@code KnowledgeExtractionScheduler} run.
 *
 * <p>Unlike {@code AnnouncementsScheduledPipeline}, {@link RssFeedCollector} has no bundled-
 * sample local/test alternative and runs in every profile - the RSS feeds are genuinely free,
 * live, and public, so there's no reason to gate the real source behind docker/prod.
 */
@Component
public class NewsFeedScheduledPipeline implements ScheduledPipeline {

    private static final String CRON_6PM_IST = "0 0 18 * * *";

    private final Collector<List<RawNewsFeedResponse>> collector;
    private final NewsFeedParser parser;
    private final NewsFeedNormalizer normalizer;
    private final NewsFeedLoader loader;

    public NewsFeedScheduledPipeline(
        @Qualifier("news-rss") Collector<List<RawNewsFeedResponse>> collector,
        NewsFeedParser parser, NewsFeedNormalizer normalizer, NewsFeedLoader loader
    ) {
        this.collector = collector;
        this.parser = parser;
        this.normalizer = normalizer;
        this.loader = loader;
    }

    @Override
    public String name() {
        return "corporate-news-rss";
    }

    @Override
    public void run(PipelineRunner runner) {
        SourceConfig sourceConfig = new SourceConfig(name(), "corporate");

        Map<String, Function<RawNewsItem, ?>> requiredFields = Map.of(
            "title", RawNewsItem::title, "link", RawNewsItem::link
        );
        Validator<RawNewsItem> validator = new RequiredFieldsValidator<>(requiredFields);

        PipelineDefinition<List<RawNewsFeedResponse>, RawNewsItem, NewsArticleDocument> definition = new PipelineDefinition<>(
            sourceConfig, collector, parser, validator, normalizer, loader
        );

        Map<String, Function<RawNewsItem, ?>> expectedFields = new HashMap<>(requiredFields);
        expectedFields.put("description", RawNewsItem::description);
        expectedFields.put("pubDate", RawNewsItem::pubDate);
        // link (not outlet) is the natural key - outlet is shared by every item from the same
        // feed, which would make duplicateRecordCount meaningless.
        DataQualitySpec<RawNewsItem> qualitySpec = new DataQualitySpec<>(
            expectedFields, requiredFields.keySet(), RawNewsItem::link
        );

        runner.run(definition, qualitySpec, CRON_6PM_IST);
    }
}
