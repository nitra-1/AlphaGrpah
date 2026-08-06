/**
 * The AI Analyst. Built in Module 2.9 inside {@code intelligence} (bridging corporate signals and
 * sector standing required it then, since domain modules never depend on each other directly -
 * docs/001_System_Architecture.md §4); relocated here in Module 3.7 when extending it to explain
 * Rank changes required {@code decision.engine.DecisionScoreReader}, which {@code intelligence}
 * cannot depend on ({@code decision} already depends on {@code intelligence}, so the reverse would
 * be circular) - and {@code decision}'s own package-info had claimed "AI analyst" as one of its
 * four capabilities since Module 0.3's original scaffold.
 *
 * <p>Per the user's explicit design, "AI explains, it never calculates" -
 * {@link com.alphagraph.decision.analyst.AnalystEvidenceBuilder} does every calculation
 * (day-over-day deltas, "highest ever" comparisons, cross-sector ranking, cross-domain score
 * deltas) deterministically, producing a list of already-verified
 * {@link com.alphagraph.decision.analyst.EvidenceFact}s;
 * {@link com.alphagraph.decision.analyst.AiAnalystClient} is the only thing that calls Claude, and
 * its prompt explicitly forbids introducing any number not already present in those facts.
 * {@link com.alphagraph.decision.analyst.AiAnalystService} is the module's only public entry
 * point, with two capabilities: explaining why an instrument's Corporate Score changed (Module
 * 2.9) and why its Swing Rank changed (Module 3.7) - not open-ended natural-language query
 * routing.
 */
package com.alphagraph.decision.analyst;
