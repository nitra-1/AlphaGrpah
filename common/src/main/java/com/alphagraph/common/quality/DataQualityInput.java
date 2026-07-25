package com.alphagraph.common.quality;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Raw counts aggregated from one batch, before they're turned into rates. A batch with zero
 * records is treated as vacuously perfect (all rates 0, completeness 1) rather than undefined —
 * there's nothing in it to be incomplete or duplicated.
 */
public record DataQualityInput(
    int totalRecords,
    int populatedFieldCount,
    int expectedFieldCount,
    int duplicateRecordCount,
    int missingRequiredFieldCount,
    int requiredFieldCount,
    int validationErrorCount
) {

    public double completeness() {
        return expectedFieldCount == 0 ? 1.0 : (double) populatedFieldCount / expectedFieldCount;
    }

    public double duplicateRate() {
        return totalRecords == 0 ? 0.0 : (double) duplicateRecordCount / totalRecords;
    }

    public double missingFieldRate() {
        return requiredFieldCount == 0 ? 0.0 : (double) missingRequiredFieldCount / requiredFieldCount;
    }

    public double validationErrorRate() {
        return totalRecords == 0 ? 0.0 : (double) validationErrorCount / totalRecords;
    }

    public static <T> DataQualityInput from(List<T> records, DataQualitySpec<T> spec, int validationErrorCount) {
        int expectedFieldCount = 0;
        int populatedFieldCount = 0;
        int requiredFieldCount = 0;
        int missingRequiredFieldCount = 0;

        for (T record : records) {
            for (Map.Entry<String, Function<T, ?>> field : spec.expectedFields().entrySet()) {
                expectedFieldCount++;
                boolean present = field.getValue().apply(record) != null;
                if (present) {
                    populatedFieldCount++;
                }
                if (spec.requiredFieldNames().contains(field.getKey())) {
                    requiredFieldCount++;
                    if (!present) {
                        missingRequiredFieldCount++;
                    }
                }
            }
        }

        int duplicateRecordCount = spec.naturalKeyExtractor() == null
            ? 0
            : countDuplicates(records, spec.naturalKeyExtractor());

        return new DataQualityInput(
            records.size(), populatedFieldCount, expectedFieldCount, duplicateRecordCount,
            missingRequiredFieldCount, requiredFieldCount, validationErrorCount
        );
    }

    /**
     * Every record sharing a key with at least one other record counts toward the duplicate
     * rate. Records with no key at all can't collide with anything by definition, and are
     * skipped rather than passed to groupingBy, which rejects null keys outright.
     */
    private static <T> int countDuplicates(List<T> records, Function<T, Object> naturalKeyExtractor) {
        return records.stream()
            .map(naturalKeyExtractor)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.groupingBy(key -> key, Collectors.counting()))
            .values().stream()
            .filter(count -> count > 1)
            .mapToInt(Long::intValue)
            .sum();
    }
}
