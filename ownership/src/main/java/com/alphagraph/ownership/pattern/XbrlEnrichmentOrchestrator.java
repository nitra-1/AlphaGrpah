package com.alphagraph.ownership.pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Drains the XBRL enrichment backlog: for each pending shareholding quarter, fetches the real
 * filing (a {@code nsearchives.nseindia.com} static archive URL - no anti-bot cookie handshake
 * needed, unlike the interactive {@code nseindia.com/api/*} endpoints; same reasoning
 * {@code reference.securitymaster.HttpSecurityMasterCollector} already relies on for its own
 * archive fetch), parses it, and writes the derived percentages.
 *
 * <p>Batched via {@link XbrlEnrichmentCandidateReader}'s {@code maxPeriods} cap, not unbounded: on
 * first deployment, tracked-symbol-count x ~20 historical quarters could mean thousands of pending
 * documents, and fetching all of them in one run risks hammering NSE during initial backfill. A
 * capped run simply leaves the remainder as candidates for the next scheduled run, draining the
 * backlog over several days - same shape as
 * {@code market.pricing.MarketPriceBackfillOrchestrator}'s own bounded walk. Once the backlog is
 * drained, daily volume per run is tiny (only newly-discovered quarters).
 *
 * <p>Per-period try/catch - one filing's fetch/parse failure is logged and skipped, never failing
 * the whole run, same convention as every other best-effort loop in this codebase.
 */
@Component
class XbrlEnrichmentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(XbrlEnrichmentOrchestrator.class);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";
    private static final int MAX_PERIODS_PER_RUN = 200;
    private static final long PER_PERIOD_PACING_MILLIS = 250;

    private final XbrlEnrichmentCandidateReader candidateReader;
    private final XbrlDocumentParser documentParser;
    private final XbrlShareholdingWriter writer;
    private final RestClient restClient;

    @Autowired
    XbrlEnrichmentOrchestrator(
        XbrlEnrichmentCandidateReader candidateReader, XbrlDocumentParser documentParser,
        XbrlShareholdingWriter writer, RestClient.Builder restClientBuilder
    ) {
        this.candidateReader = candidateReader;
        this.documentParser = documentParser;
        this.writer = writer;
        this.restClient = restClientBuilder.defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT).build();
    }

    void run() {
        var pending = candidateReader.findPending(MAX_PERIODS_PER_RUN);

        int enriched = 0;
        int failed = 0;
        for (PendingXbrlPeriod period : pending) {
            try {
                String xml = fetchDocument(period.xbrlUrl());
                Map<XbrlCategory, BigDecimal> categories = documentParser.parse(xml);
                writer.write(period.instrumentId(), period.periodEnd(), categories, period.storedPromoterPercentage(), period.symbol());
                enriched++;
            } catch (Exception e) {
                failed++;
                log.warn("Failed to enrich XBRL for {} {}: {}", period.symbol(), period.periodEnd(), e.getMessage());
            }
            sleepQuietly(PER_PERIOD_PACING_MILLIS);
        }

        log.info(
            "XBRL enrichment run complete: {} periods enriched, {} failed, {} candidates processed (cap {})",
            enriched, failed, pending.size(), MAX_PERIODS_PER_RUN
        );
    }

    private String fetchDocument(String url) {
        try {
            String body = restClient.get().uri(url).retrieve().body(String.class);
            if (body == null || body.isBlank()) {
                throw new IllegalStateException("Empty XBRL response from " + url);
            }
            return body;
        } catch (RestClientException e) {
            throw new IllegalStateException("Failed to fetch XBRL document " + url + ": " + e.getMessage(), e);
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
