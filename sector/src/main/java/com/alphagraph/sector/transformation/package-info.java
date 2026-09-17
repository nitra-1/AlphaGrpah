/**
 * Sector Context evidence (Multibagger Discovery Stage 1, Tier 5) - the write side only. The
 * computation itself lives in {@code intelligence.sectorcontext} (needs both market's price
 * history and sector's own mapping/scores, so it's built as an intelligence bridge, same
 * convention {@code intelligence.sector.SectorAnalysisOrchestrator} already uses) - this package
 * exists so {@code sector}'s own schema is still written by a class {@code sector} itself owns.
 */
package com.alphagraph.sector.transformation;
