package com.alphagraph.corporate.newsfeed;

import com.alphagraph.common.etl.Normalizer;
import com.alphagraph.corporate.api.DocumentSource;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * PIB's feed carries no {@code pubDate} per item (a real, disclosed format gap - see
 * {@link RawNewsItem}) - {@link #announcedAt} falls back to the collection time (via the
 * injected {@link Clock}) rather than fabricating a specific past timestamp. Economic
 * Times/LiveMint's real RFC-822 pubDate values ("Tue, 04 Aug 2026 21:25:24 +0530") parse via
 * {@link DateTimeFormatter#RFC_1123_DATE_TIME} directly - it's designed for exactly this format.
 */
@Component
public class NewsFeedNormalizer implements Normalizer<RawNewsItem, NewsArticleDocument> {

    private static final Pattern HTML_TAGS = Pattern.compile("<[^>]+>");

    private final Clock clock;

    public NewsFeedNormalizer() {
        this(Clock.system(ZoneId.of("Asia/Kolkata")));
    }

    /** Package-private: lets tests inject a fixed Clock instead of depending on the real time. */
    NewsFeedNormalizer(Clock clock) {
        this.clock = clock;
    }

    @Override
    public NewsArticleDocument normalize(RawNewsItem raw) {
        Instant announcedAt = parsePubDate(raw.pubDate()).orElseGet(() -> Instant.now(clock));

        String description = HTML_TAGS.matcher(raw.description()).replaceAll("").trim();
        String extractedText = description.isBlank() ? raw.title() : raw.title() + ". " + description;

        return new NewsArticleDocument(
            DocumentSource.NEWS, raw.link(), raw.outlet(), raw.title(), raw.link(), announcedAt, extractedText
        );
    }

    private static Optional<Instant> parsePubDate(String pubDate) {
        if (pubDate == null || pubDate.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(DateTimeFormatter.RFC_1123_DATE_TIME.parse(pubDate.trim(), Instant::from));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
