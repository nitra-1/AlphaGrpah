/**
 * Bridges market's published price/delivery history to the Institutional Engine (Module 1.7),
 * same pattern as {@code intelligence.technical} (Module 1.5): {@code ownership} cannot read
 * {@code market}'s tables itself (docs/001_System_Architecture.md §4 Rule 3), so
 * {@code intelligence} reads market's {@code DailyPriceReader}, maps it into ownership's own
 * {@code DeliveryVolumeBar} shape, and combines it with ownership's own shareholding/bulk-deal
 * data before calling the engine.
 */
package com.alphagraph.intelligence.institutional;
