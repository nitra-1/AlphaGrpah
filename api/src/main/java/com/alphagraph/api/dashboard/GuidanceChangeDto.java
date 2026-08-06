package com.alphagraph.api.dashboard;

import java.time.Instant;

/** "Management Guidance Changes" widget row. */
public record GuidanceChangeDto(String symbol, String metricType, String guidanceValue, String guidancePeriod, String direction, String commitmentLevel, Instant observedAt) {
}
