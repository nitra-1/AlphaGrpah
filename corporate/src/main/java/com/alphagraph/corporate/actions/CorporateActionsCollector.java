package com.alphagraph.corporate.actions;

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
 * Reads the bundled corporate-actions snapshot named by {@code sourceConfig}'s "resourcePath"
 * property - real FY25 final dividends for RELIANCE/TCS/INFY/HDFCBANK/ICICIBANK/ESCORTS (the same
 * facts originally researched for Module 1.4, reshaped from CSV into NSE's real JSON response
 * shape so {@link CorporateActionsParser} handles both the sample and the live feed identically)
 * plus one real ZOMATO row proving the quarantine path. {@code @Qualifier("corporate-actions")}
 * matters for the same reason every other dual-collector module's does:
 * {@link HttpCorporateActionsCollector} needs runtime swapping via {@code @Profile}, and without a
 * qualifier any other module's {@code Collector<String>} bean (e.g.
 * {@code corporate.documents.AnnouncementsCollector}) is an equally valid candidate for the same
 * generic type.
 *
 * <p>Only the fallback for a profile with no live source wired - same reasoning as
 * {@code corporate.documents.AnnouncementsCollector}.
 */
@Component
@Profile("!docker & !prod & !local")
@Qualifier("corporate-actions")
public class CorporateActionsCollector implements Collector<String> {

    @Override
    public String fetch(SourceConfig sourceConfig) {
        String resourcePath = sourceConfig.properties().get("resourcePath");
        try (InputStream in = CorporateActionsCollector.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Resource not found on classpath: " + resourcePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
