package com.alphagraph.common.etl;

import java.util.List;

/** Outcome of validating a single record. Never thrown — bad records are quarantined, not fatal. */
public record ValidationResult(boolean passed, List<String> errors) {

    public ValidationResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static ValidationResult valid() {
        return new ValidationResult(true, List.of());
    }

    public static ValidationResult invalid(String... errors) {
        return new ValidationResult(false, List.of(errors));
    }
}
