package com.alphagraph.api.portfolio;

/**
 * Which method actually produced {@link RankDeteriorationLevel} - {@code RANK_FRACTION} (the
 * preferred {@code rank / universeSize} calculation) or {@code RAW_RANK_FALLBACK} (raw places
 * moved, used only when {@code swingRankUniverseSize} is missing on the entry or current score -
 * a real, disclosed accuracy degradation for dates before the Learning Readiness Hardening slice
 * added universe-size provenance, not equivalent precision to the preferred path).
 */
enum RankDeteriorationBasis {
    RANK_FRACTION,
    RAW_RANK_FALLBACK
}
