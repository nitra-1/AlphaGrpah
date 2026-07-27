package com.alphagraph.market.pricing;

import com.alphagraph.common.etl.Collector;
import com.alphagraph.common.etl.SourceConfig;
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
 * Deterministic and offline-safe, so this is what local dev/CI use — {@link HttpBhavdataCollector}
 * takes over in docker/prod.
 */
@Component
@Profile("!docker & !prod")
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
