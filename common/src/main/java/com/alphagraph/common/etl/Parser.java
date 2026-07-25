package com.alphagraph.common.etl;

import java.util.List;

/** Converts a raw fetched form into structured, source-shaped records. */
@FunctionalInterface
public interface Parser<R, T> {

    List<T> parse(R raw);
}
