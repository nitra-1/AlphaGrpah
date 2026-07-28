/**
 * Pure, unit-testable indicator math (SMA, RSI, MACD, ADX, ATR, OBV, relative volume, weekly
 * resampling). No I/O, no Spring beans — {@link com.alphagraph.technical.engine.TechnicalEngine}
 * calls these directly over a {@link com.alphagraph.technical.api.TechnicalEngineInput}'s bars.
 */
package com.alphagraph.technical.indicators;
