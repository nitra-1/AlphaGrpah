/**
 * Bridges four domains to the Risk Engine (Module 1.9) - the most cross-cutting bridge yet.
 * {@code risk} cannot read {@code technical}, {@code financial}, or {@code ownership} directly
 * (docs/001_System_Architecture.md §4 Rule 3), so {@code intelligence} reads each domain's latest
 * already-computed score (plus market's price and financial's raw EPS/PAT/TotalEquity for the
 * Valuation derivation) and assembles a single {@code RiskEngineInput} before calling the engine.
 */
package com.alphagraph.intelligence.risk;
