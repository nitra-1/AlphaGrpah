/**
 * Risk/Contradiction Stage 2 (docs/007_Stage2_Inflection_Specification.md §13) - the last of the 6
 * Stage 2 families, a meta-layer whose source is 4 already-built families' own Stage 2 states
 * (Financial, Ownership, Market, Capital Allocation), not raw Stage 1 evidence directly. Reads
 * {@code financial}/{@code market}/{@code ownership}/{@code corporate}'s Stage 2 tables directly by
 * raw SQL, cross-schema by value only - {@code intelligence} already has an approved Gradle
 * dependency on the first three (confirmed via {@code intelligence.risk.RiskAnalysisOrchestrator}'s
 * real imports), and raw SQL avoids adding a new one for {@code corporate}, which it doesn't
 * otherwise depend on. Persists via {@code risk.contradiction.RiskContradictionWriter} - a domain's
 * own schema is always written by a class that domain module owns, never directly from
 * {@code intelligence}, same convention {@code intelligence.sectorcontext} already established.
 */
package com.alphagraph.intelligence.riskcontradiction;
