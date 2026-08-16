package com.alphagraph.api.portfolio;

/**
 * How much attention a position deserves - deliberately not called "urgency," which would read as
 * "act now." This platform never tells a user what to do; it only says how much a position is
 * worth looking at.
 */
enum AttentionLevel {
    LOW,
    MEDIUM,
    HIGH
}
