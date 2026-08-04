package com.alphagraph.corporate.newsfeed;

import com.alphagraph.common.etl.Collector;
import com.alphagraph.common.etl.SourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fetches real, live, public RSS feeds - Economic Times Markets, LiveMint Markets, and PIB
 * (Press Information Bureau, English press releases) - all verified live during development
 * (real, current-dated items as of verification). Two candidate feeds were tried and dropped as
 * genuinely unusable, a real disclosed gap rather than silently worked around: Moneycontrol's RSS
 * endpoints (business.xml, marketreports.xml, latestnews.xml) all return HTTP 200 but every item
 * is frozen at April 2024 - a dead feed masquerading as a live one; Business Standard's RSS
 * returns HTTP 403 (Akamai bot-block), same class of problem NSE's api.* endpoints needed a
 * cookie-bootstrap workaround for, not pursued further given two solid working general-news
 * sources plus PIB already cover all four of the roadmap's named source categories (Business
 * News, Government Notifications, Industry News, Sector News - the latter two differentiated by
 * Stage 1's topic classification, not by a dedicated per-topic feed).
 *
 * <p>One feed failing (a transient network error, a feed going the way Moneycontrol's did) does
 * NOT fail the whole collection - each feed is fetched independently and a failure is logged and
 * skipped, since these are genuinely independent sources, unlike bhavdata's single all-or-nothing
 * file. Only a total failure (every feed unreachable) throws.
 */
@Component
@Qualifier("news-rss")
public class RssFeedCollector implements Collector<List<RawNewsFeedResponse>> {

    private static final Logger log = LoggerFactory.getLogger(RssFeedCollector.class);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    private final RestClient restClient;
    private final Map<String, String> feedsByOutlet;

    @Autowired
    public RssFeedCollector(
        RestClient.Builder restClientBuilder,
        @Value("${alphagraph.corporate.news-feed-economic-times:https://economictimes.indiatimes.com/markets/rssfeeds/1977021501.cms}")
        String economicTimesUrl,
        @Value("${alphagraph.corporate.news-feed-livemint:https://www.livemint.com/rss/markets}")
        String livemintUrl,
        @Value("${alphagraph.corporate.news-feed-pib:https://www.pib.gov.in/RssMain.aspx?ModId=6&Lang=1&Regid=3&reg=48}")
        String pibUrl
    ) {
        this(restClientBuilder, linkedFeeds(economicTimesUrl, livemintUrl, pibUrl));
    }

    /** Package-private: lets tests inject a smaller/mocked feed map. */
    RssFeedCollector(RestClient.Builder restClientBuilder, Map<String, String> feedsByOutlet) {
        this.restClient = restClientBuilder.defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT).build();
        this.feedsByOutlet = feedsByOutlet;
    }

    private static Map<String, String> linkedFeeds(String economicTimesUrl, String livemintUrl, String pibUrl) {
        Map<String, String> feeds = new LinkedHashMap<>();
        feeds.put("Economic Times Markets", economicTimesUrl);
        feeds.put("LiveMint Markets", livemintUrl);
        feeds.put("PIB", pibUrl);
        return feeds;
    }

    @Override
    public List<RawNewsFeedResponse> fetch(SourceConfig sourceConfig) {
        List<RawNewsFeedResponse> responses = new ArrayList<>();
        for (Map.Entry<String, String> feed : feedsByOutlet.entrySet()) {
            try {
                String xml = restClient.get().uri(feed.getValue()).retrieve().body(String.class);
                if (xml == null || xml.isBlank()) {
                    log.warn("Empty response fetching {} feed from {}", feed.getKey(), feed.getValue());
                    continue;
                }
                responses.add(new RawNewsFeedResponse(feed.getKey(), xml));
            } catch (RestClientException e) {
                log.warn("Failed to fetch {} feed from {}: {}", feed.getKey(), feed.getValue(), e.getMessage());
            }
        }

        if (responses.isEmpty()) {
            throw new IllegalStateException("All " + feedsByOutlet.size() + " configured news feeds failed - no items to collect");
        }
        return responses;
    }
}
