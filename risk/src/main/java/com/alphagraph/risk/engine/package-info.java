/**
 * The Risk Engine: {@link com.alphagraph.risk.engine.RiskEngine} implements
 * {@code common.engine.Engine<RiskEngineInput, RiskScore>}. The most cross-cutting engine yet -
 * its input aggregates already-computed output from three domain modules' engines (Technical,
 * Fundamental, Institutional/Ownership - Modules 1.5-1.7) plus raw market/financial figures, all
 * assembled by {@code intelligence.risk} since {@code risk} may never import another domain
 * module directly (docs/001_System_Architecture.md §4, Rule 3).
 */
package com.alphagraph.risk.engine;
