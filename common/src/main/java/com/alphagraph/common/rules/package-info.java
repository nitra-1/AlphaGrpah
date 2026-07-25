/**
 * Rule Engine: replaces hard-coded thresholds ({@code if (rsi > 60)}) with configurable,
 * versioned {@link com.alphagraph.common.rules.Rule} data evaluated by a
 * {@link com.alphagraph.common.rules.RuleEvaluator} — see docs/002_Engine_Architecture.md §4.
 * Mirrors {@code common.rule_definitions} / {@code common.rule_conditions}
 * (docs/003_Database_Architecture.md §3); loading the active version of a Rule from that table
 * is a persistence concern left to whichever module consumes this in Phase 1.
 */
package com.alphagraph.common.rules;
