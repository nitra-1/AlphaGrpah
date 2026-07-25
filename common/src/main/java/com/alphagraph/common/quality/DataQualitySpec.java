package com.alphagraph.common.quality;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * What a domain module tells the Data Quality Engine about one record type: which fields are
 * expected (for completeness), which of those are required (for missing-field rate), and how
 * to derive a natural key for duplicate detection within a batch. {@code naturalKeyExtractor}
 * is optional — pass null to skip duplicate detection for record types with no natural key.
 */
public record DataQualitySpec<T>(
    Map<String, Function<T, ?>> expectedFields,
    Set<String> requiredFieldNames,
    Function<T, Object> naturalKeyExtractor
) {

    public DataQualitySpec {
        expectedFields = Map.copyOf(expectedFields);
        requiredFieldNames = Set.copyOf(requiredFieldNames);
    }

    public DataQualitySpec(Map<String, Function<T, ?>> expectedFields, Set<String> requiredFieldNames) {
        this(expectedFields, requiredFieldNames, null);
    }
}
