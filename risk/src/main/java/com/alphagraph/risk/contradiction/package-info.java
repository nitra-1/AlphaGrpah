/**
 * Storage for the Risk/Contradiction Stage 2 family (docs/007_Stage2_Inflection_Specification.md
 * §13) - a meta-layer over other domains' already-computed Stage 2 states, computed in
 * {@code intelligence.riskcontradiction} since {@code risk} may never import another domain module
 * directly (docs/001_System_Architecture.md §4, Rule 3), written here because a domain's own
 * schema is always written by a class that domain module owns. Unrelated to
 * {@code risk.engine}'s own aggregate {@code risk_scores} concept.
 */
package com.alphagraph.risk.contradiction;
