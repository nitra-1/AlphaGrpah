package com.alphagraph.common.etl;

/** Persists a canonical domain record. Upsert semantics — idempotent on the record's natural key. */
@FunctionalInterface
public interface Loader<D> {

    void load(D domainRecord);
}
