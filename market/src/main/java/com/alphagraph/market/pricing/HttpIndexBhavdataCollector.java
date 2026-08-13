package com.alphagraph.market.pricing;

import com.alphagraph.common.etl.Collector;
import com.alphagraph.common.etl.SourceConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Fetches NSE's real daily index-closing report (ind_close_all) - Outcome Evidence Enrichment's
 * market-benchmark price source. Verified live against the real endpoint
 * (https://archives.nseindia.com/content/indices/ind_close_all_DDMMYYYY.csv) before writing this -
 * a real, distinct file from the equity bhavdata report, ~148 indices, plain comma-delimited
 * (no space), confirmed to already publish same-day (unlike the equity file, which typically isn't
 * available until after market close).
 *
 * <p>Applies the same truncation-safety validation added to {@link HttpBhavdataCollector} after
 * that class was found silently accepting a truncated response for two weeks: a
 * {@code Content-Length} cross-check plus a minimum-line floor, both rejecting the response before
 * it ever reaches the parser rather than letting a truncated file parse as if it were real.
 * {@link #MIN_EXPECTED_LINES} is sized for this file specifically (real files observed at ~149
 * lines), much smaller than the equity file's threshold - the two collectors are unrelated files
 * with unrelated expected sizes.
 *
 * <p>No bundled-sample fallback for local/CI profiles, unlike {@code BhavdataCollector}/
 * {@code HttpBhavdataCollector}'s pair - nothing else in this codebase requires index price data
 * to exist for tests to pass; {@code BenchmarkReturnCalculator} already handles its absence
 * gracefully (returns {@code UNAVAILABLE}, never fabricates). Disclosed scope decision, not an
 * oversight.
 */
@Component
@Profile({"docker", "prod", "local"})
@Qualifier("marketIndex")
public class HttpIndexBhavdataCollector implements Collector<List<String>> {

    private static final DateTimeFormatter URL_DATE_FORMAT = DateTimeFormatter.ofPattern("ddMMyyyy");
    private static final int MIN_EXPECTED_LINES = 50;

    private final RestClient restClient;
    private final String urlTemplate;
    private final Clock clock;

    @Autowired
    public HttpIndexBhavdataCollector(
        RestClient.Builder restClientBuilder,
        @Value("${alphagraph.market.nse-index-url-template:https://archives.nseindia.com/content/indices/ind_close_all_%s.csv}")
        String urlTemplate
    ) {
        this(restClientBuilder, urlTemplate, Clock.system(ZoneId.of("Asia/Kolkata")));
    }

    /** Package-private: lets tests inject a fixed Clock instead of depending on the real date. */
    HttpIndexBhavdataCollector(RestClient.Builder restClientBuilder, String urlTemplate, Clock clock) {
        this.restClient = restClientBuilder
            .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; AlphaGraph/1.0)")
            .build();
        this.urlTemplate = urlTemplate;
        this.clock = clock;
    }

    @Override
    public List<String> fetch(SourceConfig sourceConfig) {
        String dateStr = LocalDate.now(clock).format(URL_DATE_FORMAT);
        String url = urlTemplate.formatted(dateStr);

        ResponseEntity<String> response;
        try {
            response = restClient.get().uri(url).retrieve().toEntity(String.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new IllegalStateException(
                "No index file for " + dateStr + " (HTTP 404) - likely a non-trading day or not yet published", e
            );
        } catch (RestClientException e) {
            throw new IllegalStateException("Failed to fetch index data for " + dateStr + ": " + e.getMessage(), e);
        }

        String body = response.getBody();
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("Empty response fetching index data for " + dateStr);
        }

        long declaredLength = response.getHeaders().getContentLength();
        long actualLength = body.getBytes(StandardCharsets.UTF_8).length;
        if (declaredLength >= 0 && declaredLength != actualLength) {
            throw new IllegalStateException(
                "Truncated response fetching index data for " + dateStr + ": server declared " + declaredLength
                    + " bytes but only " + actualLength + " were received"
            );
        }

        List<String> lines = body.lines().toList();
        if (lines.size() < MIN_EXPECTED_LINES) {
            throw new IllegalStateException(
                "Suspiciously small index response for " + dateStr + ": only " + lines.size()
                    + " lines (expected at least " + MIN_EXPECTED_LINES + ") - likely a truncated download, not a real file"
            );
        }

        return lines;
    }
}
