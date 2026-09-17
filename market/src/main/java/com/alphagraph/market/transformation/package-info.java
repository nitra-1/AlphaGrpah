/**
 * Market Accumulation evidence (Multibagger Discovery Stage 1) - daily-cadence
 * relative-volume/delivery/price-return evidence computed from {@code market.daily_prices}, the
 * same module that already owns that data. No cross-module dependency: {@code RelativeVolume} etc.
 * are independently reimplemented here (not reused from {@code technical.indicators}) because
 * domain modules never depend on each other directly (docs/001_System_Architecture.md §4 Rule 3).
 */
package com.alphagraph.market.transformation;
