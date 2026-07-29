/**
 * Bridges market's published price/volume history to the Sector Engine (Module 1.8), same
 * pattern as {@code intelligence.technical} (Module 1.5) and {@code intelligence.institutional}
 * (Module 1.7): {@code sector} cannot read {@code market}'s tables itself
 * (docs/001_System_Architecture.md §4 Rule 3), so {@code intelligence} reads market's
 * {@code DailyPriceReader}, maps it into sector's own {@code SectorBar} shape, and combines it
 * with sector's own sector-mapping data (read directly - reference is a shared kernel, not a
 * restricted peer domain) before calling the engine.
 */
package com.alphagraph.intelligence.sector;
