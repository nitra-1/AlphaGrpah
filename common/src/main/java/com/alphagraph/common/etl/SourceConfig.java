package com.alphagraph.common.etl;

import java.util.Map;

/**
 * Identifies a data source to a {@link Collector} without the Collector needing to know the
 * target schema. {@code properties} carries source-specific config (URLs, file paths, API keys)
 * that only the Collector implementation for that source interprets.
 */
public record SourceConfig(String name, String module, Map<String, String> properties) {

    public SourceConfig {
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }

    public SourceConfig(String name, String module) {
        this(name, module, Map.of());
    }
}
