package com.alphagraph.ownership.pattern;

import com.alphagraph.common.etl.Collector;
import com.alphagraph.common.etl.SourceConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Fetches NSE's real, live, per-symbol shareholding-pattern feed for the tracked universe
 * (verified live: {@code https://www.nseindia.com/api/corporate-share-holdings-master?index=equities&symbol=X}
 * returned 22 real quarters of history for RELIANCE in one call, back to September 2021, including
 * a linked real XBRL filing per quarter - see {@code XbrlEnrichmentOrchestrator}).
 *
 * <p><b>This is a new architectural pattern for this codebase, not existing convention.</b> Every
 * other live collector ({@code market.pricing.HttpBhavdataCollector},
 * {@code ownership.deals.HttpBulkDealsCollector}, {@code corporate.documents.HttpAnnouncementsCollector},
 * {@code corporate.actions.HttpCorporateActionsCollector}) does one whole-market fetch per run;
 * {@code market.pricing.MarketPriceBackfillOrchestrator}'s own javadoc documents choosing "one
 * fetch covers every candidate, not one fetch per symbol" as deliberate. Shareholding has no
 * whole-market equivalent - NSE's endpoint requires {@code symbol=} - so per-symbol fetching is
 * justified here, but it needs its own resilience the whole-market collectors get for free from
 * fetching only once: a paced, retried, failure-isolated loop, one request per tracked symbol.
 *
 * <p>Same anti-bot handshake as every other live NSE collector: a two-step cookie bootstrap - GET
 * a normal page first, replay its Set-Cookie values on every API call - done once per run, not
 * once per symbol. Per-symbol resilience, explicit: a modest fixed delay between requests (NSE's
 * interactive {@code api/*} endpoints are more sensitive to repeated automated requests than the
 * static archive CSVs the whole-market collectors read); up to {@value #MAX_ATTEMPTS} attempts per
 * symbol with exponential backoff, scoped to transient failures (429, 5xx, connection errors) -
 * never retried on a 4xx that isn't 429/403; a 403 triggers exactly one session-cookie
 * re-bootstrap plus one retry, not an unbounded loop. One symbol's exhausted retries are logged and
 * skipped - modeled on {@code market.pricing.HistoricalBackfillService}'s per-day try/catch -
 * never failing the whole run. Success/failure counts per symbol are logged at the end so a
 * degraded run is visible without inspecting individual log lines.
 */
@Component
@Profile({"docker", "prod", "local"})
@Qualifier("shareholding-pattern")
public class HttpShareholdingCollector implements Collector<String> {

    private static final Logger log = LoggerFactory.getLogger(HttpShareholdingCollector.class);

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";
    private static final int MAX_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MILLIS = 1000;
    private static final long PER_SYMBOL_PACING_MILLIS = 400;

    private final RestClient restClient;
    private final String cookieBootstrapUrl;
    private final String shareholdingUrlTemplate;
    private final OwnershipInstrumentLookup instrumentLookup;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public HttpShareholdingCollector(
        RestClient.Builder restClientBuilder,
        @Value("${alphagraph.ownership.nse-cookie-bootstrap-url:https://www.nseindia.com/option-chain}")
        String cookieBootstrapUrl,
        @Value("${alphagraph.ownership.nse-shareholding-url-template:https://www.nseindia.com/api/corporate-share-holdings-master?index=equities&symbol=%s}")
        String shareholdingUrlTemplate,
        OwnershipInstrumentLookup instrumentLookup
    ) {
        this.restClient = restClientBuilder.defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT).build();
        this.cookieBootstrapUrl = cookieBootstrapUrl;
        this.shareholdingUrlTemplate = shareholdingUrlTemplate;
        this.instrumentLookup = instrumentLookup;
    }

    @Override
    public String fetch(SourceConfig sourceConfig) {
        String cookieHeader = bootstrapCookies();
        List<String> symbols = instrumentLookup.findAllSymbols();

        ArrayNode combined = objectMapper.createArrayNode();
        int succeeded = 0;
        int failed = 0;

        for (String symbol : symbols) {
            try {
                List<JsonNode> quarters = fetchOneSymbolWithRetry(symbol, cookieHeader);
                for (JsonNode quarter : quarters) {
                    ObjectNode withSymbol = quarter.deepCopy();
                    withSymbol.put("symbol", symbol);
                    combined.add(withSymbol);
                }
                succeeded++;
            } catch (Exception e) {
                failed++;
                log.warn("Failed to fetch shareholding pattern for symbol {}: {}", symbol, e.getMessage());
            }
            sleepQuietly(PER_SYMBOL_PACING_MILLIS);
        }

        log.info("Shareholding pattern fetch complete: {} symbols succeeded, {} failed, {} total quarters", succeeded, failed, combined.size());

        if (succeeded == 0) {
            throw new IllegalStateException("Failed to fetch shareholding pattern for every one of " + symbols.size() + " tracked symbols");
        }

        return combined.toString();
    }

    private List<JsonNode> fetchOneSymbolWithRetry(String symbol, String cookieHeader) {
        String url = shareholdingUrlTemplate.formatted(symbol);
        String currentCookieHeader = cookieHeader;
        boolean rebootstrappedOnce = false;

        Exception lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String body = restClient.get().uri(url)
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .header(HttpHeaders.REFERER, "https://www.nseindia.com/companies-listing/corporate-filings-shareholding-pattern")
                    .header(HttpHeaders.COOKIE, currentCookieHeader)
                    .retrieve().body(String.class);

                if (body == null || body.isBlank()) {
                    throw new IllegalStateException("Empty response fetching shareholding pattern for " + symbol);
                }
                JsonNode root = objectMapper.readTree(body);
                return root.isArray() ? toList(root) : List.of();
            } catch (RestClientResponseException e) {
                lastFailure = e;
                HttpStatusCode status = e.getStatusCode();
                if (status.value() == 403 && !rebootstrappedOnce) {
                    rebootstrappedOnce = true;
                    currentCookieHeader = bootstrapCookies();
                    continue;
                }
                if (!isRetryable(status) || attempt == MAX_ATTEMPTS) {
                    throw e;
                }
                backoff(attempt);
            } catch (RestClientException | java.io.IOException e) {
                lastFailure = e;
                if (attempt == MAX_ATTEMPTS) {
                    throw new IllegalStateException("Failed to fetch shareholding pattern for " + symbol + ": " + e.getMessage(), e);
                }
                backoff(attempt);
            }
        }
        throw new IllegalStateException("Exhausted retries fetching shareholding pattern for " + symbol, lastFailure);
    }

    private static boolean isRetryable(HttpStatusCode status) {
        return status.value() == 429 || status.is5xxServerError();
    }

    private static void backoff(int attempt) {
        sleepQuietly(BASE_BACKOFF_MILLIS * (1L << (attempt - 1)));
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static List<JsonNode> toList(JsonNode arrayNode) {
        return java.util.stream.StreamSupport.stream(arrayNode.spliterator(), false).collect(Collectors.toList());
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
