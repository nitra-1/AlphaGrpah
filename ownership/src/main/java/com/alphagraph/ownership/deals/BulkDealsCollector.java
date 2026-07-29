package com.alphagraph.ownership.deals;

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
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the bundled real bulk/block deals snapshot (real NSE data at the time it was fetched -
 * see the CSVs' own commit for the date) named by {@code sourceConfig}'s "bulkResourcePath" and
 * "blockResourcePath" properties. Tags each row with its source ("BULK" or "BLOCK") since neither
 * real file carries that distinction itself, then emits one combined synthetic header followed by
 * both files' data rows - {@link BulkDealsParser} treats the result as a single feed.
 *
 * {@code @Qualifier("ownership-bulk-deals")} matters here for the same reason market's
 * {@code @Qualifier("market")} does (Module 1.1/1.2): {@link HttpBulkDealsCollector} needs
 * runtime swapping with this class via {@code @Profile}, and without a qualifier any other
 * module's {@code Collector<List<String>>} bean is an equally valid candidate for the exact same
 * generic type as far as Spring's concerned.
 */
@Component
@Profile("!docker & !prod")
@Qualifier("ownership-bulk-deals")
public class BulkDealsCollector implements Collector<List<String>> {

    static final String COMBINED_HEADER = "DEAL_TYPE,DATE,SYMBOL,SECURITY_NAME,CLIENT_NAME,BUY_SELL,QUANTITY,PRICE,REMARKS";

    @Override
    public List<String> fetch(SourceConfig sourceConfig) {
        String bulkResourcePath = sourceConfig.properties().get("bulkResourcePath");
        String blockResourcePath = sourceConfig.properties().get("blockResourcePath");

        List<String> combined = new ArrayList<>();
        combined.add(COMBINED_HEADER);
        combined.addAll(readAndTag(bulkResourcePath, "BULK"));
        combined.addAll(readAndTag(blockResourcePath, "BLOCK"));
        return combined;
    }

    private static List<String> readAndTag(String resourcePath, String dealType) {
        return readLines(resourcePath).stream()
            .skip(1)
            .filter(line -> !line.isBlank())
            .filter(line -> !line.startsWith("NO RECORDS"))
            .map(line -> dealType + "," + line)
            .toList();
    }

    private static List<String> readLines(String resourcePath) {
        try (InputStream in = BulkDealsCollector.class.getClassLoader().getResourceAsStream(resourcePath)) {
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
