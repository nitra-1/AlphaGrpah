package com.alphagraph.common.etl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Generic, shared validator: fails a record if any named field extractor returns null.
 * Per docs/002_Engine_Architecture.md §2 — validators are composable and can be shared across
 * modules rather than reimplemented per source.
 */
public final class RequiredFieldsValidator<T> implements Validator<T> {

    private final Map<String, Function<T, ?>> requiredFields;

    public RequiredFieldsValidator(Map<String, Function<T, ?>> requiredFields) {
        this.requiredFields = Map.copyOf(requiredFields);
    }

    @Override
    public ValidationResult validate(T record) {
        List<String> errors = new ArrayList<>();
        requiredFields.forEach((fieldName, extractor) -> {
            if (extractor.apply(record) == null) {
                errors.add(fieldName + " is required");
            }
        });
        return errors.isEmpty() ? ValidationResult.valid() : ValidationResult.invalid(errors.toArray(String[]::new));
    }
}
