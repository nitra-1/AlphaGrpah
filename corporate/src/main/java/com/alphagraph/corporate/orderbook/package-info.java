/**
 * Module 2.4: Order Book Engine. Unlike {@link com.alphagraph.corporate.events} (topic-matching
 * classification), this engine's aggregation half is a genuine {@code common.engine.Engine} -
 * summing an instrument's order-lifecycle ledger into numeric metrics (current order book value,
 * growth %, execution visibility, order count) and banding them into an
 * {@link com.alphagraph.corporate.api.OrderQuality} via {@code common.rules.RuleSet} threshold
 * rules, exactly like every Phase 1 engine. Reads canonical facts (Module 2.2's output) - never
 * re-parses documents or calls Claude.
 */
package com.alphagraph.corporate.orderbook;
