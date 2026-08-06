package com.alphagraph.api.dashboard;

import java.time.Instant;

/** "Today's Biggest Orders" widget row. {@code customerName} is already resolved - never a raw entity id. */
public record BiggestOrderDto(String symbol, Double orderValueCrore, String customerName, String lifecycleStage, Instant detectedAt) {
}
