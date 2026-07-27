package com.alphagraph.market.pricing;

import com.alphagraph.common.etl.Collector;
import com.alphagraph.common.etl.SourceConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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
 */
@Component
@Profile({"docker", "prod"})
public class HttpBhavdataCollector implements Collector<List<String>> {

    private static final DateTimeFormatter URL_DATE_FORMAT = DateTimeFormatter.ofPattern("ddMMyyyy");

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

        String body;
        try {
            body = restClient.get().uri(url).retrieve().body(String.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new IllegalStateException(
                "No bhavdata file for " + dateStr + " (HTTP 404) - likely a non-trading day or not yet published", e
            );
        } catch (RestClientException e) {
            throw new IllegalStateException("Failed to fetch bhavdata for " + dateStr + ": " + e.getMessage(), e);
        }

        if (body == null || body.isBlank()) {
            throw new IllegalStateException("Empty response fetching bhavdata for " + dateStr);
        }

        return body.lines().toList();
    }
}
