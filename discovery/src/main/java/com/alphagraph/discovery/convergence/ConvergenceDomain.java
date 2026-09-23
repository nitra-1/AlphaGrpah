package com.alphagraph.discovery.convergence;

/**
 * The 5 positive convergence domains (docs/008's own Stage 4 hand-off + the user's Stage 4 spec
 * §6-7) - max breadth is 5, never 6. Risk is deliberately not a member: it is consumed as a
 * contradiction overlay only, never counted toward breadth (see
 * {@link RiskContradictionOverlayReader}).
 */
enum ConvergenceDomain {
    FINANCIAL,
    OWNERSHIP,
    MARKET,
    SECTOR,
    CAPITAL_ALLOCATION
}
