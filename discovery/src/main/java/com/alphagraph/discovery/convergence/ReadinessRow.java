package com.alphagraph.discovery.convergence;

/** A source domain's own Stage 3 readiness, the most recent as-of a given date. {@code historyCoverage} carries whatever that domain's own coverage column means (sessions/periods/days) - not used numerically by Stage 4's own scoring, kept for explainability only. */
record ReadinessRow(SourceReadiness readiness, int historyCoverage) {
}
