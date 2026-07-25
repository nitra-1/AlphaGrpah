package com.alphagraph.common.rules;

import java.util.List;

/**
 * A named, versioned thing being evaluated against one metric (e.g. "RSI Overbought" targeting
 * "rsi"). Which version is the currently active one is a persistence concern
 * (docs/003_Database_Architecture.md §3's partial unique index) — this type just represents
 * whichever version the caller already loaded.
 */
public record Rule(String name, String targetMetric, int version, List<RuleCondition> conditions) {

    public Rule {
        conditions = List.copyOf(conditions);
    }
}
