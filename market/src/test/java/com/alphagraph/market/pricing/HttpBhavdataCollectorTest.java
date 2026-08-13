package com.alphagraph.market.pricing;

import com.alphagraph.common.etl.SourceConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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

    /** A real bhavcopy lists thousands of securities; this builds a realistically-sized fixture. */
    private static String realisticSizedBody(int dataRows) {
        String header = "SYMBOL, SERIES";
        return Stream.concat(Stream.of(header), IntStream.range(0, dataRows).mapToObj(i -> "SYM" + i + ", EQ"))
            .reduce((a, b) -> a + "\n" + b)
            .orElse(header);
    }

    @Test
    void successfulFetchReturnsEachLine() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String body = realisticSizedBody(600);
        server.expect(requestTo(EXPECTED_URL)).andRespond(withSuccess(body, MediaType.TEXT_PLAIN));

        HttpBhavdataCollector collector = new HttpBhavdataCollector(builder, URL_TEMPLATE, FIXED_CLOCK);

        List<String> lines = collector.fetch(sourceConfig);

        assertThat(lines).hasSize(601).startsWith("SYMBOL, SERIES", "SYM0, EQ");
        server.verify();
    }

    @Test
    void suspiciouslySmallResponseIsTreatedAsATruncatedDownload() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(EXPECTED_URL))
            .andRespond(withSuccess("SYMBOL, SERIES\n20MICRONS, EQ", MediaType.TEXT_PLAIN));

        HttpBhavdataCollector collector = new HttpBhavdataCollector(builder, URL_TEMPLATE, FIXED_CLOCK);

        assertThatIllegalStateException()
            .isThrownBy(() -> collector.fetch(sourceConfig))
            .withMessageContaining("Suspiciously small")
            .withMessageContaining("truncated download");
    }

    @Test
    void contentLengthMismatchIsTreatedAsATruncatedResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String body = realisticSizedBody(600);
        long actualBytes = body.getBytes(StandardCharsets.UTF_8).length;
        server.expect(requestTo(EXPECTED_URL))
            .andRespond(withSuccess(body, MediaType.TEXT_PLAIN)
                .headers(headersWithContentLength(actualBytes * 2)));

        HttpBhavdataCollector collector = new HttpBhavdataCollector(builder, URL_TEMPLATE, FIXED_CLOCK);

        assertThatIllegalStateException()
            .isThrownBy(() -> collector.fetch(sourceConfig))
            .withMessageContaining("Truncated response");
    }

    private static HttpHeaders headersWithContentLength(long declaredLength) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentLength(declaredLength);
        return headers;
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
