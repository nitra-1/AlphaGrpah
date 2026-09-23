package com.alphagraph.discovery.convergence;

/**
 * A domain's status as of a Stage 4 snapshot - separate from any individual Stage 3 sequence's
 * own phase. {@code ACTIVE}: a qualifying (non-{@code BROKEN}) sequence exists whose own {@code
 * last_step_date} is within that domain's freshness window. {@code STALE}: a qualifying sequence
 * exists but its evidence is older than the window - excluded from breadth/maturity/confidence,
 * but preserved for explainability. {@code BROKEN}: only {@code BROKEN} sequences are on record.
 * {@code NO_ACTIVE_SEQUENCE}: the domain returned no sequence rows at all. {@code
 * INSUFFICIENT_DATA}: that domain's own Stage 3 readiness isn't usable ({@code
 * MISSING_PREREQUISITE_DATA}/{@code INSUFFICIENT_HISTORY}/absent) - distinct from {@code
 * NO_ACTIVE_SEQUENCE}, which means real readiness exists but nothing has formed yet.
 */
enum DomainContributionStatus {
    ACTIVE,
    STALE,
    BROKEN,
    INSUFFICIENT_DATA,
    NO_ACTIVE_SEQUENCE
}
