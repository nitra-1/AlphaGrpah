package com.alphagraph.api.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Checks, right now, whether every external source AlphaGraph's collectors depend on is actually
 * reachable - reads the exact same {@literal @}Value-configured URLs/templates the real collectors
 * use (same property names and defaults), so this never drifts from what the app would actually
 * call. This exists because those endpoints (mostly NSE archive/API paths) are undocumented and
 * have no stability guarantee - the whole point is to catch a silent shape change before the next
 * cron fire does, per the user's explicit "endpoints may undergo change in future" framing.
 *
 * A GET (not HEAD) is used throughout because NSE's archive hosts don't reliably answer HEAD, and
 * the response body is discarded (toBodilessEntity) so this stays a lightweight liveness probe,
 * not a real fetch. Each check gets its own short timeout and they all run in parallel, so total
 * wall time for the whole set stays close to the slowest single source rather than their sum.
 */
@Service
public class LiveSourceHealthService {

    private static final Duration TIMEOUT = Duration.ofSeconds(6);
    private static final DateTimeFormatter NSE_DATE_FORMAT = DateTimeFormatter.ofPattern("ddMMyyyy");
    private static final DateTimeFormatter ANNOUNCEMENTS_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final RestClient restClient;
    private final Clock clock = Clock.system(ZoneId.of("Asia/Kolkata"));

    private final String bhavdataUrlTemplate;
    private final String bulkDealsUrl;
    private final String blockDealsUrl;
    private final String securityMasterUrl;
    private final String nseCookieBootstrapUrl;
    private final String announcementsUrlTemplate;
    private final String nlpSidecarBaseUrl;
    private final String newsFeedEconomicTimes;
    private final String newsFeedLivemint;
    private final String newsFeedPib;
    private final String anthropicApiKey;

    public LiveSourceHealthService(
        @Value("${alphagraph.market.nse-bhavdata-url-template:https://archives.nseindia.com/products/content/sec_bhavdata_full_%s.csv}")
        String bhavdataUrlTemplate,
        @Value("${alphagraph.ownership.nse-bulk-deals-url:https://archives.nseindia.com/content/equities/bulk.csv}")
        String bulkDealsUrl,
        @Value("${alphagraph.ownership.nse-block-deals-url:https://archives.nseindia.com/content/equities/block.csv}")
        String blockDealsUrl,
        @Value("${alphagraph.reference.security-master-url:https://nsearchives.nseindia.com/content/equities/EQUITY_L.csv}")
        String securityMasterUrl,
        @Value("${alphagraph.corporate.nse-cookie-bootstrap-url:https://www.nseindia.com/option-chain}")
        String nseCookieBootstrapUrl,
        @Value("${alphagraph.corporate.nse-announcements-url-template:https://www.nseindia.com/api/corporate-announcements?index=equities&from_date=%s&to_date=%s}")
        String announcementsUrlTemplate,
        @Value("${alphagraph.nlp-sidecar.base-url:http://localhost:8000}")
        String nlpSidecarBaseUrl,
        @Value("${alphagraph.corporate.news-feed-economic-times:https://economictimes.indiatimes.com/markets/rssfeeds/1977021501.cms}")
        String newsFeedEconomicTimes,
        @Value("${alphagraph.corporate.news-feed-livemint:https://www.livemint.com/rss/markets}")
        String newsFeedLivemint,
        @Value("${alphagraph.corporate.news-feed-pib:https://www.pib.gov.in/RssMain.aspx?ModId=6&Lang=1&Regid=3&reg=48}")
        String newsFeedPib
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) TIMEOUT.toMillis());
        requestFactory.setReadTimeout((int) TIMEOUT.toMillis());
        this.restClient = RestClient.builder()
            .requestFactory(requestFactory)
            .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; AlphaGraph/1.0)")
            .build();

        this.bhavdataUrlTemplate = bhavdataUrlTemplate;
        this.bulkDealsUrl = bulkDealsUrl;
        this.blockDealsUrl = blockDealsUrl;
        this.securityMasterUrl = securityMasterUrl;
        this.nseCookieBootstrapUrl = nseCookieBootstrapUrl;
        this.announcementsUrlTemplate = announcementsUrlTemplate;
        this.nlpSidecarBaseUrl = nlpSidecarBaseUrl;
        this.newsFeedEconomicTimes = newsFeedEconomicTimes;
        this.newsFeedLivemint = newsFeedLivemint;
        this.newsFeedPib = newsFeedPib;
        this.anthropicApiKey = System.getenv("ANTHROPIC_API_KEY");
    }

    public List<LiveSourceStatusDto> checkAll() {
        LocalDate today = LocalDate.now(clock);
        String bhavdataDate = today.format(NSE_DATE_FORMAT);
        String announcementsDate = today.format(ANNOUNCEMENTS_DATE_FORMAT);

        List<CompletableFuture<LiveSourceStatusDto>> checks = List.of(
            checkAsync("NSE Bhavcopy (daily prices)", bhavdataUrlTemplate.formatted(bhavdataDate)),
            checkAsync("NSE Bulk Deals", bulkDealsUrl),
            checkAsync("NSE Block Deals", blockDealsUrl),
            checkAsync("NSE Security Master", securityMasterUrl),
            checkAsync("NSE Cookie Bootstrap (used by Announcements)", nseCookieBootstrapUrl),
            checkAsync("NSE Corporate Announcements", announcementsUrlTemplate.formatted(announcementsDate, announcementsDate)),
            checkAsync("NLP Sidecar", nlpSidecarBaseUrl + "/health"),
            checkAsync("RSS: Economic Times Markets", newsFeedEconomicTimes),
            checkAsync("RSS: Livemint Markets", newsFeedLivemint),
            checkAsync("RSS: PIB", newsFeedPib),
            CompletableFuture.supplyAsync(this::checkAnthropic)
        );

        List<LiveSourceStatusDto> results = new ArrayList<>();
        for (CompletableFuture<LiveSourceStatusDto> check : checks) {
            results.add(check.join());
        }
        return results;
    }

    private CompletableFuture<LiveSourceStatusDto> checkAsync(String name, String url) {
        return CompletableFuture.supplyAsync(() -> check(name, url));
    }

    private LiveSourceStatusDto check(String name, String url) {
        Instant checkedAt = Instant.now();
        long start = System.currentTimeMillis();
        try {
            HttpStatusCode status = restClient.get().uri(url).retrieve().toBodilessEntity().getStatusCode();
            long latency = System.currentTimeMillis() - start;
            if (status.is2xxSuccessful()) {
                return new LiveSourceStatusDto(name, url, "UP", status.value(), latency, checkedAt, null);
            }
            return new LiveSourceStatusDto(name, url, "DEGRADED", status.value(), latency, checkedAt,
                "Reachable but returned HTTP " + status.value());
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            long latency = System.currentTimeMillis() - start;
            return new LiveSourceStatusDto(name, url, "DEGRADED", e.getStatusCode().value(), latency, checkedAt,
                "Reachable but returned HTTP " + e.getStatusCode().value());
        } catch (RestClientException e) {
            long latency = System.currentTimeMillis() - start;
            return new LiveSourceStatusDto(name, url, "DOWN", null, latency, checkedAt, e.getMessage());
        }
    }

    private LiveSourceStatusDto checkAnthropic() {
        String name = "Anthropic API";
        String url = "https://api.anthropic.com/v1/models";
        if (anthropicApiKey == null || anthropicApiKey.isBlank()) {
            return new LiveSourceStatusDto(name, url, "NOT_CONFIGURED", null, 0, Instant.now(),
                "ANTHROPIC_API_KEY is not set in this environment");
        }
        Instant checkedAt = Instant.now();
        long start = System.currentTimeMillis();
        try {
            HttpStatusCode status = restClient.get().uri(url)
                .header("x-api-key", anthropicApiKey)
                .header("anthropic-version", "2023-06-01")
                .retrieve().toBodilessEntity().getStatusCode();
            long latency = System.currentTimeMillis() - start;
            if (status.is2xxSuccessful()) {
                return new LiveSourceStatusDto(name, url, "UP", status.value(), latency, checkedAt, null);
            }
            return new LiveSourceStatusDto(name, url, "DEGRADED", status.value(), latency, checkedAt,
                "Reachable but returned HTTP " + status.value());
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            long latency = System.currentTimeMillis() - start;
            return new LiveSourceStatusDto(name, url, "DEGRADED", e.getStatusCode().value(), latency, checkedAt,
                "Reachable but returned HTTP " + e.getStatusCode().value());
        } catch (RestClientException e) {
            long latency = System.currentTimeMillis() - start;
            return new LiveSourceStatusDto(name, url, "DOWN", null, latency, checkedAt, e.getMessage());
        }
    }
}
