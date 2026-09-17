/**
 * Sector Context evidence (Multibagger Discovery Stage 1, Tier 5 of the remaining 7 evidence
 * families) - the "full triangle" of stock vs. Nifty, stock vs. its own sector, and sector vs.
 * Nifty, from one instrument-level engine. Built here rather than in {@code sector} itself: this
 * needs both market's price-return evidence and sector's own mapping/scores, and
 * {@code sector} cannot depend on {@code market} directly (docs/001_System_Architecture.md §4
 * Rule 3) - same cross-domain-bridge convention {@code intelligence.sector.SectorAnalysisOrchestrator}
 * already uses. Reads {@code market.transformation_evidence} directly by raw SQL, cross-schema by
 * value only (no Java dependency on {@code market.transformation}'s package-private classes) -
 * {@code intelligence} already has an approved Gradle dependency on {@code market}, so this is
 * even more permitted than the {@code market.pricing.DiscoveryCandidateLookup}/
 * {@code ownership.discovery_status} precedent this pattern is modeled on. Persists via
 * {@code sector.transformation.SectorContextEvidenceWriter} - a domain's own schema is always
 * written by a class that domain module owns, never directly from {@code intelligence}.
 */
package com.alphagraph.intelligence.sectorcontext;
