/**
 * Stage 4 Cross-Domain Convergence - the layer after Stage 3 (docs/008's own hand-off point):
 * consumes Market/Ownership/Financial/Sector/Capital Allocation's own Stage 3 {@code
 * transformation_sequences} tables directly by raw SQL, plus {@code risk.contradiction_states} as
 * a contradiction overlay (never a 6th positive domain), and detects whether multiple independent
 * domains are transforming at the same time for the same instrument. Lives in its own module
 * ({@code discovery}) rather than {@code intelligence} - {@code intelligence} owns zero Flyway
 * migrations/schema anywhere, so a layer that needs to persist its own cross-domain output needs
 * a module that owns a schema, the same way {@code decision}/{@code learning} already do, not a
 * split between compute (intelligence) and persistence (somewhere else) for no benefit. Not in
 * {@code ModuleBoundaryArchTest}'s {@code DOMAIN_MODULES} list, so - like {@code intelligence}/
 * {@code decision}/{@code learning} - it's allowed to depend on every domain module directly.
 */
package com.alphagraph.discovery.convergence;
