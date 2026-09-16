package com.alphagraph.ownership.pattern;

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
 * Reads the bundled shareholding snapshot named by {@code sourceConfig}'s "resourcePath" property
 * - real FY25/FY24 percentages originally researched for Module 1.2, reshaped from CSV into NSE's
 * real per-symbol JSON envelope so {@link ShareholdingParser} handles both the sample and the live
 * feed identically. {@code @Qualifier("shareholding-pattern")} matters for the same reason every
 * other dual-collector module's does: {@link HttpShareholdingCollector} needs runtime swapping via
 * {@code @Profile}, and without a qualifier any other module's {@code Collector<String>} bean is an
 * equally valid candidate for the same generic type.
 *
 * <p>Only the fallback for a profile with no live source wired - same reasoning as
 * {@code corporate.actions.CorporateActionsCollector}.
 */
@Component
@Profile("!docker & !prod & !local")
@Qualifier("shareholding-pattern")
public class ShareholdingCollector implements Collector<String> {

    @Override
    public String fetch(SourceConfig sourceConfig) {
        String resourcePath = sourceConfig.properties().get("resourcePath");
        try (InputStream in = ShareholdingCollector.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Resource not found on classpath: " + resourcePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
