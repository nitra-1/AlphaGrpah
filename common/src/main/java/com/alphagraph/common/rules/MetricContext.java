package com.alphagraph.common.rules;

import java.util.Map;

/**
 * Already-computed metrics for one entity (e.g. one stock on one date). The evaluator only
 * reads from this — it never fetches data itself, per docs/002_Engine_Architecture.md §4.
 */
public record MetricContext(Map<String, Double> metrics) {

    public MetricContext {
        metrics = Map.copyOf(metrics);
    }

    public Double get(String metricName) {
        return metrics.get(metricName);
    }
}
