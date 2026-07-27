package com.alphagraph.market.pricing;

import com.alphagraph.common.etl.SourceConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpBhavdataCollectorTest {

    // Fixed to 24-Jul-2026 (Asia/Kolkata) so the expected URL is deterministic in tests.
    private static final Clock FIXED_CLOCK = Clock.fixed(
        Instant.parse("2026-07-24T12:00:00Z"), ZoneId.of("Asia/Kolkata")
    );
    private static final String URL_TEMPLATE = "https://example-nse-mirror.test/sec_bhavdata_full_%s.csv";
    private static final String EXPECTED_URL = "https://example-nse-mirror.test/sec_bhavdata_full_24072026.csv";

    private final SourceConfig sourceConfig = new SourceConfig("nse-daily-bhavdata", "market");

    @Test
    void successfulFetchReturnsEachLine() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(EXPECTED_URL))
            .andRespond(withSuccess("SYMBOL, SERIES\nACE, EQ", MediaType.TEXT_PLAIN));

        HttpBhavdataCollector collector = new HttpBhavdataCollector(builder, URL_TEMPLATE, FIXED_CLOCK);

        List<String> lines = collector.fetch(sourceConfig);

        assertThat(lines).containsExactly("SYMBOL, SERIES", "ACE, EQ");
        server.verify();
    }

    @Test
    void notFoundProducesADistinctMessageFromOtherFailures() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(EXPECTED_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        HttpBhavdataCollector collector = new HttpBhavdataCollector(builder, URL_TEMPLATE, FIXED_CLOCK);

        assertThatIllegalStateException()
            .isThrownBy(() -> collector.fetch(sourceConfig))
            .withMessageContaining("404")
            .withMessageContaining("non-trading day");
    }

    @Test
    void serverErrorProducesADifferentMessageThanNotFound() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(EXPECTED_URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        HttpBhavdataCollector collector = new HttpBhavdataCollector(builder, URL_TEMPLATE, FIXED_CLOCK);

        assertThatIllegalStateException()
            .isThrownBy(() -> collector.fetch(sourceConfig))
            .withMessageContaining("Failed to fetch")
            .withMessageContaining("24072026");
    }

    @Test
    void emptyResponseBodyIsTreatedAsAFailure() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(EXPECTED_URL)).andRespond(withSuccess("", MediaType.TEXT_PLAIN));

        HttpBhavdataCollector collector = new HttpBhavdataCollector(builder, URL_TEMPLATE, FIXED_CLOCK);

        assertThatIllegalStateException()
            .isThrownBy(() -> collector.fetch(sourceConfig))
            .withMessageContaining("Empty response");
    }
}
