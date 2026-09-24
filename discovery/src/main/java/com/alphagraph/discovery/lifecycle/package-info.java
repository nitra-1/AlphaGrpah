/**
 * Stage 5 Lifecycle Classification - the layer after Stage 4: consumes {@code
 * discovery.convergence_snapshots}/{@code _domain_contributions} directly (never Stage 3, never
 * recalculating Stage 3/4 logic) and classifies each instrument's transformation *trajectory*
 * into one of 7 lifecycle states. Lives in this sibling package inside the same {@code discovery}
 * module rather than a new module (per the user's own instruction) - {@code discovery.convergence}
 * is entirely package-private, so this package genuinely cannot import its records even though
 * it's the same Gradle module (Java package-private visibility is per-package, not per-module) -
 * every reader here owns its own raw SQL and its own DTOs, exactly mirroring how {@code
 * discovery.convergence}'s own 5 domain-sequence readers already read Stage 3's package-private
 * records via raw SQL, one layer up.
 */
package com.alphagraph.discovery.lifecycle;
