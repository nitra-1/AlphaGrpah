package com.alphagraph.common.etl;

/** Checks structural/business validity of a single record without mutating it. */
@FunctionalInterface
public interface Validator<T> {

    ValidationResult validate(T record);
}
