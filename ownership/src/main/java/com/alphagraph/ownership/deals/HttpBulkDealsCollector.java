package com.alphagraph.ownership.deals;

import com.alphagraph.common.etl.Collector;
import com.alphagraph.common.etl.SourceConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;

/**
 * Fetches NSE's real, live bulk and block deals reports. Unlike
 * {@code market.pricing.HttpBhavdataCollector}, neither URL takes a date parameter - they always
 * serve the current day's deals, so there is no historical archive to request against; this
 * collector only ever captures "today". A day with zero block deals returns a body that's just
 * "NO RECORDS,,,,,,\n" after the header - a real, valid empty state, not an error.
 *
 * Active in {@code local} as well as {@code docker}/{@code prod}, same reasoning and same fix as
 * {@code market.pricing.HttpBhavdataCollector}: {@code local} is what this app is actually run
 * under day to day, and the bundled fallback ({@link BulkDealsCollector}) turned out to be worse
 * than merely stale - its sample data covers symbols this project has never tracked (AASTHA,
 * AGARIND, AQYLON), so every row was silently rejected as "Unknown instrument" on every run,
 * 100% of the time, for as long as this pipeline has existed in local dev.
 */
@Component
@Profile({"docker", "prod", "local"})
@Qualifier("ownership-bulk-deals")
public class HttpBulkDealsCollector implements Collector<List<String>> {

    private final RestClient restClient;
    private final String bulkDealsUrl;
    private final String blockDealsUrl;

    @Autowired
    public HttpBulkDealsCollector(
        RestClient.Builder restClientBuilder,
        @Value("${alphagraph.ownership.nse-bulk-deals-url:https://archives.nseindia.com/content/equities/bulk.csv}")
        String bulkDealsUrl,
        @Value("${alphagraph.ownership.nse-block-deals-url:https://archives.nseindia.com/content/equities/block.csv}")
        String blockDealsUrl
    ) {
        this.restClient = restClientBuilder
            .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; AlphaGraph/1.0)")
            .build();
        this.bulkDealsUrl = bulkDealsUrl;
        this.blockDealsUrl = blockDealsUrl;
    }

    @Override
    public List<String> fetch(SourceConfig sourceConfig) {
        List<String> combined = new ArrayList<>();
        combined.add(BulkDealsCollector.COMBINED_HEADER);
        combined.addAll(fetchAndTag(bulkDealsUrl, "BULK"));
        combined.addAll(fetchAndTag(blockDealsUrl, "BLOCK"));
        return combined;
    }

    private List<String> fetchAndTag(String url, String dealType) {
        String body;
        try {
            body = restClient.get().uri(url).retrieve().body(String.class);
        } catch (RestClientException e) {
            throw new IllegalStateException("Failed to fetch " + dealType + " deals from " + url + ": " + e.getMessage(), e);
        }

        if (body == null || body.isBlank()) {
            throw new IllegalStateException("Empty response fetching " + dealType + " deals from " + url);
        }

        return body.lines()
            .skip(1)
            .filter(line -> !line.isBlank())
            .filter(line -> !line.startsWith("NO RECORDS"))
            .map(line -> dealType + "," + line)
            .toList();
    }
}
