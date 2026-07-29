package com.alphagraph.financial.api;

import java.util.List;
import java.util.UUID;

/**
 * The Fundamental Engine's input for one instrument: every known period, ordered ascending by
 * {@link FinancialResult#periodEnd()}. Unlike the Technical Engine's daily bars (Module 1.5),
 * this list is typically short (often just 1-2 real periods) - Growth metrics need a same-type
 * prior period and are correctly reported as unavailable when there isn't one.
 */
public record FundamentalEngineInput(UUID instrumentId, String symbol, List<FinancialResult> periodsAscending) {

    public FundamentalEngineInput {
        periodsAscending = List.copyOf(periodsAscending);
    }
}
