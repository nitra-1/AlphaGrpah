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
 * Fetches the real NSE sec_bhavdata_full report over HTTP. A 404 gets a distinct message from
 * every other failure ("no file for this date" — a non-trading day or not-yet-published, versus
 * a real transport/format problem) so a human reading pipeline_execution_errors or the notify
 * log can tell the difference at a glance. Either way the pipeline still ends up FAILED — the
 * point of the 404 distinction is diagnosis, not different resilience behavior.
 *
 * The URL template is configurable (alphagraph.market.nse-bhavdata-url-template) so a change to
 * NSE's URL path is a config update, not a redeploy. A change to the file's actual format would
 * still need code changes to BhavdataParser.
 *
 * Active in {@code local} as well as {@code docker}/{@code prod}: {@code local} is what the user
 * actually runs this app under day to day, so it needs the real daily price to stay current the
 * same way docker/prod would - a bundled sample dated once and never refreshed defeats the point
 * of a "daily" pipeline for whoever is actually using the app. Parses whatever the live file
 * contains for every symbol currently in reference.instruments (BhavdataNormalizer looks each one
 * up dynamically), so this automatically covers every tracked instrument, not a fixed list -
 * newly-added instruments are picked up the next run with no code change.
 *
 * <p>Validates the response before handing it to the parser. Confirmed live (2026-08-13): for
 * about two weeks this collector intermittently received a genuine but truncated prefix of the
 * real file (~9 lines instead of ~2,400+) - the rejected symbols in pipeline_execution_errors
 * during that window were real NSE tickers (e.g. 20MICRONS, GRAPHITE) that appear at the very
 * start of the file, proving the download was cut short mid-stream rather than blocked or
 * replaced by an error page. Because a handful of rows still parsed, the run only ever reported
 * itself as routine PARTIAL, so the data loss went unnoticed. {@link #MIN_EXPECTED_LINES} rejects
 * any response far smaller than a real bhavcopy ever is (NSE's daily file lists thousands of
 * securities across all series, not just the platform's tracked universe), and the Content-Length
 * cross-check catches a truncation even on a slow trading day, whenever the server declares one.
 */
@Component
@Profile({"docker", "prod", "local"})
@Qualifier("market")
public class HttpBhavdataCollector implements Collector<List<String>> {

    private static final DateTimeFormatter URL_DATE_FORMAT = DateTimeFormatter.ofPattern("ddMMyyyy");

    /**
     * NSE's daily bhavcopy lists thousands of securities across all series (EQ, bonds, T-bills,
     * ETFs...) - real files observed range ~2,400-2,460 lines. 500 leaves generous headroom for a
     * legitimately quiet day while still catching a truncated download by a wide margin.
     */
    private static final int MIN_EXPECTED_LINES = 500;

    private final RestClient restClient;
    private final String urlTemplate;
    private final Clock clock;

    @Autowired
    public HttpBhavdataCollector(
        RestClient.Builder restClientBuilder,
        @Value("${alphagraph.market.nse-bhavdata-url-template:https://archives.nseindia.com/products/content/sec_bhavdata_full_%s.csv}")
        String urlTemplate
    ) {
        this(restClientBuilder, urlTemplate, Clock.system(ZoneId.of("Asia/Kolkata")));
    }

    /** Package-private: lets tests inject a fixed Clock instead of depending on the real date. */
    HttpBhavdataCollector(RestClient.Builder restClientBuilder, String urlTemplate, Clock clock) {
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
                "No bhavdata file for " + dateStr + " (HTTP 404) - likely a non-trading day or not yet published", e
            );
        } catch (RestClientException e) {
            throw new IllegalStateException("Failed to fetch bhavdata for " + dateStr + ": " + e.getMessage(), e);
        }

        String body = response.getBody();
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("Empty response fetching bhavdata for " + dateStr);
        }

        long declaredLength = response.getHeaders().getContentLength();
        long actualLength = body.getBytes(StandardCharsets.UTF_8).length;
        if (declaredLength >= 0 && declaredLength != actualLength) {
            throw new IllegalStateException(
                "Truncated response fetching bhavdata for " + dateStr + ": server declared " + declaredLength
                    + " bytes but only " + actualLength + " were received"
            );
        }

        List<String> lines = body.lines().toList();
        if (lines.size() < MIN_EXPECTED_LINES) {
            throw new IllegalStateException(
                "Suspiciously small bhavdata response for " + dateStr + ": only " + lines.size()
                    + " lines (expected at least " + MIN_EXPECTED_LINES + ") - likely a truncated download, not a real file"
            );
        }

        return lines;
    }
}
