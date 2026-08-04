/**
 * Module 2.5: Management Commentary Engine. Two layers, mirroring
 * {@link com.alphagraph.corporate.orderbook}'s exact shape: Layer 1 (immutable, one row per
 * forward-looking statement) parsed from canonical facts (Module 2.2's output, written by
 * {@code corporate.knowledge.ManagementExtractor}); Layer 2 (derived, a genuine
 * {@code common.engine.Engine} implementation) aggregates an instrument's observation history
 * into a Growth Visibility score, Guidance Trend, and Management Credibility rating via
 * {@code common.rules.RuleSet} threshold rules.
 */
package com.alphagraph.corporate.commentary;
