package com.alphagraph.corporate.api;

/**
 * The overall investment-thesis direction of a detected {@link EventType} - distinct from
 * {@code category}, which classifies the event's financial nature (e.g. "Revenue Positive").
 * Kept as a separate field rather than derived from category, since the two can genuinely diverge
 * (e.g. a debt raise might be category "Financing" but signal Neutral or Negative depending on
 * terms, while a promoter sale is category "Ownership" but signal is not automatically Negative).
 */
public enum EventSignal {
    POSITIVE,
    NEGATIVE,
    NEUTRAL
}
