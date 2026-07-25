package com.alphagraph.common.engine;

import java.time.Instant;

/** Minimal concrete {@link Score} — what {@link NullEngine} returns; real engines may need richer types. */
public record SimpleScore(double value, double confidence, int ruleSetVersion, Instant computedAt) implements Score {
}
