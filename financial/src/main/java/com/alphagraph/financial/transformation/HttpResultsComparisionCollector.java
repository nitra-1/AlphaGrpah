package com.alphagraph.financial.transformation;

import com.alphagraph.financial.results.FinancialInstrumentLookup;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.stream.StreamSupport;

/**
 * Fetches NSE's real, live, per-symbol quarterly results-comparison feed for the tracked universe
 * (verified live: {@code https://www.nseindia.com/api/results-comparision?symbol=X} returns
 * exactly 5 real quarters per symbol, most recent first). Same per-symbol resilience shape as
 * {@code ownership.pattern.HttpShareholdingCollector} - a paced, retried, failure-isolated loop,
 * one request per tracked symbol, since this endpoint has no whole-market equivalent either.
 *
 * <p>Not typed against {@code common.etl.Collector<String>} - {@code financial.results
 * .FinancialResultsScheduledPipeline}'s own javadoc already establishes this module's convention
 * of depending on the concrete collector class directly when there's only one real implementation,
 * rather than an interface with nothing else to swap against.
 */
@Component
@Profile({"docker", "prod", "local"})
public class HttpResultsComparisionCollector {

    private static final Logger log = LoggerFactory.getLogger(HttpResultsComparisionCollector.class);

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";
    private static final int MAX_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MILLIS = 1000;
    private static final long PER_SYMBOL_PACING_MILLIS = 400;

    private final RestClient restClient;
    private final String cookieBootstrapUrl;
    private final String resultsComparisionUrlTemplate;
    private final FinancialInstrumentLookup instrumentLookup;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public HttpResultsComparisionCollector(
        RestClient.Builder restClientBuilder,
        @Value("${alphagraph.financial.nse-cookie-bootstrap-url:https://www.nseindia.com/option-chain}")
        String cookieBootstrapUrl,
        @Value("${alphagraph.financial.nse-results-comparision-url-template:https://www.nseindia.com/api/results-comparision?symbol=%s}")
        String resultsComparisionUrlTemplate,
        FinancialInstrumentLookup instrumentLookup
    ) {
        this.restClient = restClientBuilder.defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT).build();
        this.cookieBootstrapUrl = cookieBootstrapUrl;
        this.resultsComparisionUrlTemplate = resultsComparisionUrlTemplate;
        this.instrumentLookup = instrumentLookup;
    }

    public String fetch() {
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
                log.warn("Failed to fetch results comparison for symbol {}: {}", symbol, e.getMessage());
            }
            sleepQuietly(PER_SYMBOL_PACING_MILLIS);
        }

        log.info("Results comparison fetch complete: {} symbols succeeded, {} failed, {} total quarters", succeeded, failed, combined.size());

        if (succeeded == 0) {
            throw new IllegalStateException("Failed to fetch results comparison for every one of " + symbols.size() + " tracked symbols");
        }

        return combined.toString();
    }

    private List<JsonNode> fetchOneSymbolWithRetry(String symbol, String cookieHeader) {
        String url = resultsComparisionUrlTemplate.formatted(symbol);
        String currentCookieHeader = cookieHeader;
        boolean rebootstrappedOnce = false;

        Exception lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String body = restClient.get().uri(url)
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .header(HttpHeaders.REFERER, "https://www.nseindia.com/get-quotes/equity?symbol=" + symbol)
                    .header(HttpHeaders.COOKIE, currentCookieHeader)
                    .retrieve().body(String.class);

                if (body == null || body.isBlank()) {
                    throw new IllegalStateException("Empty response fetching results comparison for " + symbol);
                }
                JsonNode root = objectMapper.readTree(body).path("resCmpData");
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
                    throw new IllegalStateException("Failed to fetch results comparison for " + symbol + ": " + e.getMessage(), e);
                }
                backoff(attempt);
            }
        }
        throw new IllegalStateException("Exhausted retries fetching results comparison for " + symbol, lastFailure);
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
        return StreamSupport.stream(arrayNode.spliterator(), false).collect(Collectors.toList());
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
