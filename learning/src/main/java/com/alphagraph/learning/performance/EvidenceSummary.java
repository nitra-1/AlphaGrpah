package com.alphagraph.learning.performance;

/** Raw evidence-accumulation counters - the honest "how much do we actually have yet" surface, shown on the Learning Dashboard alongside (gated) hit rates. */
public record EvidenceSummary(int daysOfHistory, long decisionSnapshotCount, long forwardOutcomesComputed) {
}
