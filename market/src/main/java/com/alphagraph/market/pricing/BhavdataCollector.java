package com.alphagraph.market.pricing;

import com.alphagraph.common.etl.Collector;
import com.alphagraph.common.etl.SourceConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Reads the bundled sample CSV named by {@code sourceConfig}'s "resourcePath" property.
 * Deterministic and offline-safe — now only the fallback for a profile that isn't wired to a real
 * feed. {@code local} used to fall back to this bean too, but that meant the user's own real
 * running instance never saw a live price update; {@link HttpBhavdataCollector} now covers
 * {@code local} as well as docker/prod, so this bean only remains active where no live source is
 * configured at all (e.g. a bare/default profile).
 *
 * {@code @Qualifier("market")} matters: any other module's Collector<List<String>> bean (e.g.
 * ownership's ShareholdingCollector) is otherwise a false-positive candidate for the exact same
 * generic type, regardless of which pipeline is asking — confirmed the hard way
 * (NoUniqueBeanDefinitionException once a second module introduced its own such collector).
 */
@Component
@Profile("!docker & !prod & !local")
@Qualifier("market")
public class BhavdataCollector implements Collector<List<String>> {

    @Override
    public List<String> fetch(SourceConfig sourceConfig) {
        String resourcePath = sourceConfig.properties().get("resourcePath");
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Resource not found on classpath: " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.lines().toList();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
