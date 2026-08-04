package com.alphagraph.corporate.newsfeed;

import com.alphagraph.common.etl.SourceConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RssFeedCollectorTest {

    private final SourceConfig sourceConfig = new SourceConfig("corporate-news-rss", "corporate");

    @Test
    void fetchesAllConfiguredFeedsSuccessfully() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://feed-a.test/rss")).andRespond(withSuccess("<rss>A</rss>", MediaType.APPLICATION_XML));
        server.expect(requestTo("https://feed-b.test/rss")).andRespond(withSuccess("<rss>B</rss>", MediaType.APPLICATION_XML));

        RssFeedCollector collector = new RssFeedCollector(builder, feeds("https://feed-a.test/rss", "https://feed-b.test/rss"));

        List<RawNewsFeedResponse> responses = collector.fetch(sourceConfig);

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(RawNewsFeedResponse::xml).containsExactly("<rss>A</rss>", "<rss>B</rss>");
        server.verify();
    }

    @Test
    void oneFeedFailingDoesNotFailTheOthers() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://feed-a.test/rss")).andRespond(withStatus(HttpStatus.FORBIDDEN));
        server.expect(requestTo("https://feed-b.test/rss")).andRespond(withSuccess("<rss>B</rss>", MediaType.APPLICATION_XML));

        RssFeedCollector collector = new RssFeedCollector(builder, feeds("https://feed-a.test/rss", "https://feed-b.test/rss"));

        List<RawNewsFeedResponse> responses = collector.fetch(sourceConfig);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).xml()).isEqualTo("<rss>B</rss>");
    }

    @Test
    void allFeedsFailingThrows() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://feed-a.test/rss")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        RssFeedCollector collector = new RssFeedCollector(builder, feeds("https://feed-a.test/rss"));

        assertThatIllegalStateException()
            .isThrownBy(() -> collector.fetch(sourceConfig))
            .withMessageContaining("All 1 configured news feeds failed");
    }

    @Test
    void emptyResponseBodyForOneFeedIsSkippedNotFailed() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://feed-a.test/rss")).andRespond(withSuccess("", MediaType.APPLICATION_XML));
        server.expect(requestTo("https://feed-b.test/rss")).andRespond(withSuccess("<rss>B</rss>", MediaType.APPLICATION_XML));

        RssFeedCollector collector = new RssFeedCollector(builder, feeds("https://feed-a.test/rss", "https://feed-b.test/rss"));

        List<RawNewsFeedResponse> responses = collector.fetch(sourceConfig);

        assertThat(responses).hasSize(1);
    }

    private Map<String, String> feeds(String... urls) {
        Map<String, String> feeds = new LinkedHashMap<>();
        for (int i = 0; i < urls.length; i++) {
            feeds.put("Feed " + (char) ('A' + i), urls[i]);
        }
        return feeds;
    }
}
