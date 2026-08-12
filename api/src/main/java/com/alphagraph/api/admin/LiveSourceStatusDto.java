package com.alphagraph.api.admin;

import java.time.Instant;

/**
 * Result of a real, live connectivity check against one external source AlphaGraph's collectors
 * depend on - checked right now, not cached from the last scheduled run, since the whole point is
 * to catch a source that has changed shape or gone away since the last cron fire. status is one
 * of UP (2xx), DEGRADED (server reachable but returned a non-2xx - e.g. NSE's anti-bot 403), DOWN
 * (connection failed or timed out), or NOT_CONFIGURED (Anthropic checks only, when
 * ANTHROPIC_API_KEY isn't set in this environment).
 */
public record LiveSourceStatusDto(
    String name,
    String url,
    String status,
    Integer httpStatus,
    long latencyMs,
    Instant checkedAt,
    String detail
) {
}
