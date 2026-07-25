package com.alphagraph.common.etl;

/** Maps a source-shaped record to the canonical domain model (units, dates, symbol resolution). */
@FunctionalInterface
public interface Normalizer<T, D> {

    D normalize(T record);
}
