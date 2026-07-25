package com.alphagraph.common.etl;

/** Fetches raw data from an external source. Knows nothing about the target schema. */
@FunctionalInterface
public interface Collector<R> {

    R fetch(SourceConfig sourceConfig);
}
