package com.alphagraph.corporate.newsfeed;

import com.alphagraph.corporate.api.DocumentSource;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class NewsFeedNormalizerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-08-04T12:00:00Z"), ZoneId.of("Asia/Kolkata"));

    private final NewsFeedNormalizer normalizer = new NewsFeedNormalizer(FIXED_CLOCK);

    @Test
    void parsesRealRfc822PubDate() {
        RawNewsItem item = new RawNewsItem(
            "Economic Times Markets", "Pfizer beats earnings estimates", "https://example.com/pfizer",
            "Pfizer reported strong Q2 results.", "Tue, 04 Aug 2026 21:25:24 +0530"
        );

        NewsArticleDocument document = normalizer.normalize(item);

        assertThat(document.announcedAt()).isEqualTo(Instant.parse("2026-08-04T15:55:24Z"));
        assertThat(document.source()).isEqualTo(DocumentSource.NEWS);
        assertThat(document.externalId()).isEqualTo("https://example.com/pfizer");
        assertThat(document.category()).isEqualTo("Economic Times Markets");
        assertThat(document.sourceUrl()).isEqualTo("https://example.com/pfizer");
        assertThat(document.extractedText()).isEqualTo("Pfizer beats earnings estimates. Pfizer reported strong Q2 results.");
    }

    @Test
    void missingPubDateFallsBackToCollectionTimeNotAFabricatedPastTime() {
        RawNewsItem item = new RawNewsItem("PIB", "Six Coal Blocks Auctioned", "https://pib.gov.in/x", "", "");

        NewsArticleDocument document = normalizer.normalize(item);

        assertThat(document.announcedAt()).isEqualTo(FIXED_CLOCK.instant());
    }

    @Test
    void missingDescriptionUsesTitleOnlyAsExtractedText() {
        RawNewsItem item = new RawNewsItem("PIB", "Six Coal Blocks Auctioned", "https://pib.gov.in/x", "", "");

        assertThat(normalizer.normalize(item).extractedText()).isEqualTo("Six Coal Blocks Auctioned");
    }

    @Test
    void htmlTagsAreStrippedFromDescription() {
        RawNewsItem item = new RawNewsItem(
            "Test", "Some title", "https://example.com/x", "Text with <a href=\"x\">a link</a> inside.", ""
        );

        assertThat(normalizer.normalize(item).extractedText()).isEqualTo("Some title. Text with a link inside.");
    }

    @Test
    void unparseablePubDateFallsBackToCollectionTime() {
        RawNewsItem item = new RawNewsItem("Test", "title", "https://example.com/x", "", "not a real date");

        assertThat(normalizer.normalize(item).announcedAt()).isEqualTo(FIXED_CLOCK.instant());
    }
}
