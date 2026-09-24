package com.alphagraph.discovery.lifecycle;

/**
 * Separate from {@link LifecycleState} on purpose - e.g. {@code EMERGING + RISING} is a
 * meaningfully different situation from {@code EMERGING + WEAKENING} even while both occupy the
 * same lifecycle state. {@code UNKNOWN} only when readiness isn't {@code READY}.
 */
enum TrajectoryDirection {
    RISING,
    STABLE,
    WEAKENING,
    RECOVERING,
    UNKNOWN
}
