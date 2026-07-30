package com.alphagraph.corporate.documents;

import com.alphagraph.common.etl.Collector;
import com.alphagraph.common.etl.SourceConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Reads the bundled real corporate-announcements snapshot named by {@code sourceConfig}'s
 * "resourcePath" property - one real, current announcement per tracked instrument plus one real
 * WIPRO row proving the quarantine path (fetched live from
 * https://www.nseindia.com/api/corporate-announcements on 2026-07-29/30, see the resource's own
 * commit). {@code @Qualifier("corporate-announcements")} matters for the same reason every other
 * dual-collector module's does (Modules 1.1/1.2/1.7): {@link HttpAnnouncementsCollector} needs
 * runtime swapping via {@code @Profile}, and without a qualifier any other module's
 * {@code Collector<String>} bean is an equally valid candidate for the same generic type.
 */
@Component
@Profile("!docker & !prod")
@Qualifier("corporate-announcements")
public class AnnouncementsCollector implements Collector<String> {

    @Override
    public String fetch(SourceConfig sourceConfig) {
        String resourcePath = sourceConfig.properties().get("resourcePath");
        try (InputStream in = AnnouncementsCollector.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Resource not found on classpath: " + resourcePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
