package com.alphagraph.corporate.documents;

import com.alphagraph.common.etl.Collector;
import com.alphagraph.common.etl.SourceConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Fetches NSE's real, live corporate-announcements feed for the whole market on the current
 * trading day (verified live: https://www.nseindia.com/api/corporate-announcements?index=equities
 * &from_date=X&to_date=X returned 897 real announcements across 460 symbols for a single day
 * during development - most rows quarantine as untracked instruments, same shape as
 * ownership.deals' bulk/block deals pipeline (Module 1.7), not a per-symbol iteration).
 *
 * Unlike {@code archives.nseindia.com}'s static CSV files (bhavdata, bulk/block deals), this is
 * an {@code nseindia.com/api/*} endpoint sitting behind NSE's anti-bot layer - it rejects requests
 * with no session cookie. The fix (confirmed live) is a real two-step handshake: GET a normal
 * page first to receive Set-Cookie headers, then replay those cookies on the API call. No CAPTCHA
 * or JS challenge was encountered in practice - a plain cookie jar is sufficient.
 */
@Component
@Profile({"docker", "prod"})
@Qualifier("corporate-announcements")
public class HttpAnnouncementsCollector implements Collector<String> {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";
    private static final DateTimeFormatter URL_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final RestClient restClient;
    private final String cookieBootstrapUrl;
    private final String announcementsUrlTemplate;
    private final Clock clock;

    @Autowired
    public HttpAnnouncementsCollector(
        RestClient.Builder restClientBuilder,
        @Value("${alphagraph.corporate.nse-cookie-bootstrap-url:https://www.nseindia.com/option-chain}")
        String cookieBootstrapUrl,
        @Value("${alphagraph.corporate.nse-announcements-url-template:https://www.nseindia.com/api/corporate-announcements?index=equities&from_date=%s&to_date=%s}")
        String announcementsUrlTemplate
    ) {
        this(restClientBuilder, cookieBootstrapUrl, announcementsUrlTemplate, Clock.system(ZoneId.of("Asia/Kolkata")));
    }

    /** Package-private: lets tests inject a fixed Clock instead of depending on the real date. */
    HttpAnnouncementsCollector(
        RestClient.Builder restClientBuilder, String cookieBootstrapUrl, String announcementsUrlTemplate, Clock clock
    ) {
        this.restClient = restClientBuilder.defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT).build();
        this.cookieBootstrapUrl = cookieBootstrapUrl;
        this.announcementsUrlTemplate = announcementsUrlTemplate;
        this.clock = clock;
    }

    @Override
    public String fetch(SourceConfig sourceConfig) {
        String cookieHeader = bootstrapCookies();

        String today = LocalDate.now(clock).format(URL_DATE_FORMAT);
        String url = announcementsUrlTemplate.formatted(today, today);

        String body;
        try {
            body = restClient.get().uri(url)
                .header(HttpHeaders.ACCEPT, "application/json")
                .header(HttpHeaders.REFERER, "https://www.nseindia.com/companies-listing/corporate-filings-announcements")
                .header(HttpHeaders.COOKIE, cookieHeader)
                .retrieve().body(String.class);
        } catch (RestClientException e) {
            throw new IllegalStateException("Failed to fetch corporate announcements for " + today + ": " + e.getMessage(), e);
        }

        if (body == null || body.isBlank()) {
            throw new IllegalStateException("Empty response fetching corporate announcements for " + today);
        }

        return body;
    }

    private String bootstrapCookies() {
        ResponseEntity<String> response;
        try {
            response = restClient.get().uri(cookieBootstrapUrl).retrieve().toEntity(String.class);
        } catch (RestClientException e) {
            throw new IllegalStateException("Failed to bootstrap NSE session cookies from " + cookieBootstrapUrl, e);
        }

        List<String> setCookieHeaders = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (setCookieHeaders == null || setCookieHeaders.isEmpty()) {
            throw new IllegalStateException("No Set-Cookie headers received from " + cookieBootstrapUrl);
        }

        return setCookieHeaders.stream()
            .map(header -> header.split(";", 2)[0])
            .collect(Collectors.joining("; "));
    }
}
