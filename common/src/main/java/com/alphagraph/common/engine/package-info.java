/**
 * Generic Scoring Engine Contract every Phase 1+ engine (Technical, Fundamental, Institutional,
 * Sector, Risk, ...) implements — see docs/002_Engine_Architecture.md §5. Phase 0 ships only the
 * contract plus a no-op {@link com.alphagraph.common.engine.NullEngine}, used to prove the
 * scheduler's Calculate -> Score -> Notify stages run end to end before any real engine exists.
 */
package com.alphagraph.common.engine;
