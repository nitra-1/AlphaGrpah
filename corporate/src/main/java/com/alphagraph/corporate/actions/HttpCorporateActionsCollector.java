package com.alphagraph.corporate.actions;

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
 * Fetches NSE's real, live corporate-actions feed for the whole market (verified live:
 * {@code https://www.nseindia.com/api/corporates-corporateActions?index=equities&from_date=X&to_date=Y}
 * returned 148 real rows across 144 distinct symbols for a 14-day window during development - only
 * 1 of the 8 tracked instruments overlapped, the same "mostly untracked" shape as
 * {@code ownership.deals}' bulk/block deals feed and this module's own corporate-announcements
 * feed). Corporate actions are disclosed ahead of their ex-date and this endpoint filters by
 * ex-date, so a single day's window (like the announcements feed uses) would miss most real
 * activity - this queries a rolling {@value #LOOKBACK_DAYS}-day backward window instead, the exact
 * window already confirmed live to return rich real data. Daily re-fetches re-cover mostly the
 * same rows, which is harmless: {@link CorporateActionsLoader} upserts on
 * {@code (instrument_id, action_type, ex_date)}.
 *
 * <p>Same anti-bot handshake as {@code corporate.documents.HttpAnnouncementsCollector} (a real
 * {@code nseindia.com/api/*} endpoint, not one of {@code archives.nseindia.com}'s static CSVs): a
 * two-step cookie bootstrap - GET a normal page first, replay its Set-Cookie values on the API
 * call. No CAPTCHA or JS challenge encountered in practice.
 *
 * <p>Active in {@code local} as well as {@code docker}/{@code prod}, same reasoning as every other
 * live NSE collector in this codebase: {@code local} is what this app is actually run under day to
 * day, and a fixed bundled sample would starve local dev of any new real corporate action for
 * every instrument added since. The bundled fallback ({@link CorporateActionsCollector}) only
 * covers a profile with no live source wired.
 */
@Component
@Profile({"docker", "prod", "local"})
@Qualifier("corporate-actions")
public class HttpCorporateActionsCollector implements Collector<String> {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";
    private static final DateTimeFormatter URL_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final int LOOKBACK_DAYS = 14;

    private final RestClient restClient;
    private final String cookieBootstrapUrl;
    private final String corporateActionsUrlTemplate;
    private final Clock clock;

    @Autowired
    public HttpCorporateActionsCollector(
        RestClient.Builder restClientBuilder,
        @Value("${alphagraph.corporate.nse-cookie-bootstrap-url:https://www.nseindia.com/option-chain}")
        String cookieBootstrapUrl,
        @Value("${alphagraph.corporate.nse-corporate-actions-url-template:https://www.nseindia.com/api/corporates-corporateActions?index=equities&from_date=%s&to_date=%s}")
        String corporateActionsUrlTemplate
    ) {
        this(restClientBuilder, cookieBootstrapUrl, corporateActionsUrlTemplate, Clock.system(ZoneId.of("Asia/Kolkata")));
    }

    /** Package-private: lets tests inject a fixed Clock instead of depending on the real date. */
    HttpCorporateActionsCollector(
        RestClient.Builder restClientBuilder, String cookieBootstrapUrl, String corporateActionsUrlTemplate, Clock clock
    ) {
        this.restClient = restClientBuilder.defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT).build();
        this.cookieBootstrapUrl = cookieBootstrapUrl;
        this.corporateActionsUrlTemplate = corporateActionsUrlTemplate;
        this.clock = clock;
    }

    @Override
    public String fetch(SourceConfig sourceConfig) {
        String cookieHeader = bootstrapCookies();

        LocalDate today = LocalDate.now(clock);
        String from = today.minusDays(LOOKBACK_DAYS).format(URL_DATE_FORMAT);
        String to = today.format(URL_DATE_FORMAT);
        String url = corporateActionsUrlTemplate.formatted(from, to);

        String body;
        try {
            body = restClient.get().uri(url)
                .header(HttpHeaders.ACCEPT, "application/json")
                .header(HttpHeaders.REFERER, "https://www.nseindia.com/companies-listing/corporate-filings-actions")
                .header(HttpHeaders.COOKIE, cookieHeader)
                .retrieve().body(String.class);
        } catch (RestClientException e) {
            throw new IllegalStateException("Failed to fetch corporate actions for " + from + " to " + to + ": " + e.getMessage(), e);
        }

        if (body == null || body.isBlank()) {
            throw new IllegalStateException("Empty response fetching corporate actions for " + from + " to " + to);
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
