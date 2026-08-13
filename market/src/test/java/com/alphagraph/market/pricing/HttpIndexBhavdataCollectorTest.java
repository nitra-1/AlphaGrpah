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

class HttpIndexBhavdataCollectorTest {

    // Fixed to 24-Jul-2026 (Asia/Kolkata) so the expected URL is deterministic in tests.
    private static final Clock FIXED_CLOCK = Clock.fixed(
        Instant.parse("2026-07-24T12:00:00Z"), ZoneId.of("Asia/Kolkata")
    );
    private static final String URL_TEMPLATE = "https://example-nse-index-mirror.test/ind_close_all_%s.csv";
    private static final String EXPECTED_URL = "https://example-nse-index-mirror.test/ind_close_all_24072026.csv";

    private final SourceConfig sourceConfig = new SourceConfig("nifty50-index", "market");

    private static String realisticSizedBody(int indexRows) {
        String header = "Index Name,Index Date,Open Index Value,High Index Value,Low Index Value,Closing Index Value,Points Change,Change(%),Volume,Turnover (Rs. Cr.),P/E,P/B,Div Yield";
        return Stream.concat(
            Stream.of(header),
            IntStream.range(0, indexRows).mapToObj(i -> "Index " + i + ",24-07-2026,100,101,99,100.5,0.5,.5,1000,10,20,3,1")
        ).reduce((a, b) -> a + "\n" + b).orElse(header);
    }

    @Test
    void successfulFetchReturnsEachLine() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String body = realisticSizedBody(60);
        server.expect(requestTo(EXPECTED_URL)).andRespond(withSuccess(body, MediaType.TEXT_PLAIN));

        HttpIndexBhavdataCollector collector = new HttpIndexBhavdataCollector(builder, URL_TEMPLATE, FIXED_CLOCK);

        List<String> lines = collector.fetch(sourceConfig);

        assertThat(lines).hasSize(61);
        server.verify();
    }

    @Test
    void notFoundProducesADistinctMessageFromOtherFailures() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(EXPECTED_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        HttpIndexBhavdataCollector collector = new HttpIndexBhavdataCollector(builder, URL_TEMPLATE, FIXED_CLOCK);

        assertThatIllegalStateException()
            .isThrownBy(() -> collector.fetch(sourceConfig))
            .withMessageContaining("404")
            .withMessageContaining("non-trading day");
    }

    @Test
    void suspiciouslySmallResponseIsTreatedAsATruncatedDownload() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(EXPECTED_URL))
            .andRespond(withSuccess("Index Name,Index Date\nNifty 50,24-07-2026", MediaType.TEXT_PLAIN));

        HttpIndexBhavdataCollector collector = new HttpIndexBhavdataCollector(builder, URL_TEMPLATE, FIXED_CLOCK);

        assertThatIllegalStateException()
            .isThrownBy(() -> collector.fetch(sourceConfig))
            .withMessageContaining("Suspiciously small")
            .withMessageContaining("truncated download");
    }

    @Test
    void contentLengthMismatchIsTreatedAsATruncatedResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String body = realisticSizedBody(60);
        long actualBytes = body.getBytes(StandardCharsets.UTF_8).length;
        server.expect(requestTo(EXPECTED_URL))
            .andRespond(withSuccess(body, MediaType.TEXT_PLAIN).headers(headersWithContentLength(actualBytes * 2)));

        HttpIndexBhavdataCollector collector = new HttpIndexBhavdataCollector(builder, URL_TEMPLATE, FIXED_CLOCK);

        assertThatIllegalStateException()
            .isThrownBy(() -> collector.fetch(sourceConfig))
            .withMessageContaining("Truncated response");
    }

    private static HttpHeaders headersWithContentLength(long declaredLength) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentLength(declaredLength);
        return headers;
    }
}
