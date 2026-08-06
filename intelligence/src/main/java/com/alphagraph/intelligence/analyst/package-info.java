/**
 * Module 2.9: AI Analyst. Lives in {@code intelligence} rather than {@code corporate} because it
 * genuinely bridges two domains (corporate signals and sector standing) - the same reason
 * {@code intelligence.risk} bridges financial/technical/ownership instead of living in any one of
 * them (domain modules never depend on each other, docs/001_System_Architecture.md §4).
 *
 * <p>Per the user's explicit design, "AI explains, it never calculates" -
 * {@link com.alphagraph.intelligence.analyst.AnalystEvidenceBuilder} does every calculation
 * (day-over-day deltas, "highest ever" comparisons, cross-sector ranking) deterministically,
 * producing a list of already-verified {@link com.alphagraph.intelligence.analyst.EvidenceFact}s;
 * {@link com.alphagraph.intelligence.analyst.AiAnalystClient} is the only thing that calls Claude,
 * and its prompt explicitly forbids introducing any number not already present in those facts.
 * {@link com.alphagraph.intelligence.analyst.AiAnalystService} is the module's only public entry
 * point, scoped to exactly the one capability the spec demonstrates - explaining why an
 * instrument's outlook changed - not open-ended natural-language query routing.
 */
package com.alphagraph.intelligence.analyst;
