package com.alphagraph.reference.securitymaster;

import com.alphagraph.common.etl.Collector;
import com.alphagraph.common.etl.SourceConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Fetches NSE's real, live, free full equity list - confirmed live during development (real
 * current listings, e.g. a company listed 20-APR-2026, present as of verification). Unlike
 * {@code market.pricing.HttpBhavdataCollector} or {@code corporate.documents.HttpAnnouncementsCollector},
 * this needs no cookie-bootstrap anti-bot workaround (confirmed: a plain GET with a browser-like
 * User-Agent succeeds) and isn't date-parameterized, so there's no bundled-sample/live-HTTP
 * profile split here - same reasoning as {@code corporate.newsfeed.RssFeedCollector}: one real,
 * simple, low-risk source, live in every profile including local dev. The file changes only when
 * NSE adds/removes/renames a listing, not daily, so a network dependency in local/CI runs is a
 * genuinely small cost.
 */
@Component
@Qualifier("security-master")
public class HttpSecurityMasterCollector implements Collector<List<String>> {

    private final RestClient restClient;
    private final String url;

    @Autowired
    public HttpSecurityMasterCollector(
        RestClient.Builder restClientBuilder,
        @Value("${alphagraph.reference.security-master-url:https://nsearchives.nseindia.com/content/equities/EQUITY_L.csv}")
        String url
    ) {
        this.restClient = restClientBuilder
            .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; AlphaGraph/1.0)")
            .build();
        this.url = url;
    }

    @Override
    public List<String> fetch(SourceConfig sourceConfig) {
        String body;
        try {
            body = restClient.get().uri(url).retrieve().body(String.class);
        } catch (RestClientException e) {
            throw new IllegalStateException("Failed to fetch NSE security master: " + e.getMessage(), e);
        }

        if (body == null || body.isBlank()) {
            throw new IllegalStateException("Empty response fetching NSE security master");
        }
        return body.lines().toList();
    }
}
