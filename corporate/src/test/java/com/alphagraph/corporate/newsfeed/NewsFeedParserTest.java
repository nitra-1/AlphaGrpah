package com.alphagraph.corporate.newsfeed;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NewsFeedParserTest {

    private final NewsFeedParser parser = new NewsFeedParser();

    @Test
    void parsesPlainRss20Item() {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?><rss version="2.0"><channel><title>Test</title>
            <item><title><![CDATA[Pfizer beats earnings estimates]]></title>
            <description><![CDATA[Pfizer reported strong Q2 results.]]></description>
            <link>https://example.com/pfizer-earnings</link>
            <pubDate>Tue, 04 Aug 2026 21:25:24 +0530</pubDate></item>
            </channel></rss>
            """;

        List<RawNewsItem> items = parser.parse(List.of(new RawNewsFeedResponse("Economic Times Markets", xml)));

        assertThat(items).hasSize(1);
        RawNewsItem item = items.get(0);
        assertThat(item.outlet()).isEqualTo("Economic Times Markets");
        assertThat(item.title()).isEqualTo("Pfizer beats earnings estimates");
        assertThat(item.description()).isEqualTo("Pfizer reported strong Q2 results.");
        assertThat(item.link()).isEqualTo("https://example.com/pfizer-earnings");
        assertThat(item.pubDate()).isEqualTo("Tue, 04 Aug 2026 21:25:24 +0530");
    }

    @Test
    void parsesFullyCdataWrappedItemLikeLiveMint() {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?><rss version="2.0"><channel>
            <item><title><![CDATA[Crude oil drops 5%]]></title>
            <link><![CDATA[https://example.com/crude-oil]]></link>
            <description><![CDATA[Crude oil prices fell sharply.]]></description>
            <pubDate><![CDATA[Tue, 04 Aug 2026 21:27:20 +0530]]></pubDate></item>
            </channel></rss>
            """;

        List<RawNewsItem> items = parser.parse(List.of(new RawNewsFeedResponse("LiveMint Markets", xml)));

        assertThat(items).hasSize(1);
        assertThat(items.get(0).title()).isEqualTo("Crude oil drops 5%");
        assertThat(items.get(0).link()).isEqualTo("https://example.com/crude-oil");
    }

    @Test
    void parsesTitleOnlyItemLikePib() {
        String xml = """
            <?xml version="1.0" encoding="utf-8"?><rss version="2.0"><channel>
            <item><title>Six Coal Blocks Successfully Auctioned</title>
            <link>https://pib.gov.in/PressReleaseIframePage.aspx?PRID=2294522</link></item>
            </channel></rss>
            """;

        List<RawNewsItem> items = parser.parse(List.of(new RawNewsFeedResponse("PIB", xml)));

        assertThat(items).hasSize(1);
        RawNewsItem item = items.get(0);
        assertThat(item.title()).isEqualTo("Six Coal Blocks Successfully Auctioned");
        assertThat(item.description()).isEmpty();
        assertThat(item.pubDate()).isEmpty();
    }

    @Test
    void itemsWithoutTitleOrLinkAreSkipped() {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?><rss version="2.0"><channel>
            <item><description>No title or link here</description></item>
            </channel></rss>
            """;

        assertThat(parser.parse(List.of(new RawNewsFeedResponse("Test", xml)))).isEmpty();
    }

    @Test
    void malformedXmlForOneFeedDoesNotFailOthers() {
        String malformed = "<rss><channel><item><title>unclosed";
        String valid = """
            <?xml version="1.0" encoding="UTF-8"?><rss version="2.0"><channel>
            <item><title>Valid item</title><link>https://example.com/valid</link></item>
            </channel></rss>
            """;

        List<RawNewsItem> items = parser.parse(List.of(
            new RawNewsFeedResponse("Broken", malformed), new RawNewsFeedResponse("Good", valid)
        ));

        assertThat(items).hasSize(1);
        assertThat(items.get(0).title()).isEqualTo("Valid item");
    }

    @Test
    void multipleItemsAcrossMultipleFeedsAreAllParsed() {
        String feedA = """
            <rss version="2.0"><channel>
            <item><title>A1</title><link>https://example.com/a1</link></item>
            <item><title>A2</title><link>https://example.com/a2</link></item>
            </channel></rss>
            """;
        String feedB = """
            <rss version="2.0"><channel>
            <item><title>B1</title><link>https://example.com/b1</link></item>
            </channel></rss>
            """;

        List<RawNewsItem> items = parser.parse(List.of(
            new RawNewsFeedResponse("A", feedA), new RawNewsFeedResponse("B", feedB)
        ));

        assertThat(items).hasSize(3);
        assertThat(items).extracting(RawNewsItem::title).containsExactly("A1", "A2", "B1");
    }
}
