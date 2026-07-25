package com.alphagraph.common.engine;

import java.time.Instant;

/**
 * Every engine's output is structurally identical regardless of domain, so the intelligence
 * module can aggregate them uniformly — see docs/002_Engine_Architecture.md §5.
 */
public interface Score {

    double value();

    double confidence();

    int ruleSetVersion();

    Instant computedAt();
}
