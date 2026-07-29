/**
 * The Institutional Engine: {@link com.alphagraph.ownership.engine.InstitutionalEngine}
 * implements {@code common.engine.Engine<InstitutionalEngineInput, InstitutionalScore>},
 * evaluating a RuleSet loaded by
 * {@link com.alphagraph.ownership.engine.InstitutionalRuleSetLoader} against promoter/FII/DII/MF
 * trend (ownership's own shareholding_pattern, Module 1.2), bulk/block deals (ownership's own
 * bulk_deals, Module 1.7), and delivery %/volume (market's, which ownership cannot read directly
 * per docs/001_System_Architecture.md §4 Rule 3 - {@code intelligence} bridges that part, same
 * pattern as Module 1.5). {@link com.alphagraph.ownership.engine.ShareholdingReader} and
 * {@link com.alphagraph.ownership.engine.BulkDealsReader} read ownership's own tables directly.
 */
package com.alphagraph.ownership.engine;
