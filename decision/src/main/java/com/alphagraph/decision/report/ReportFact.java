package com.alphagraph.decision.report;

/**
 * One already-verified, already-worded fact about the day - every number in {@code description}
 * was computed and substituted in by Java, not by the LLM. Mirrors
 * intelligence.analyst.EvidenceFact's exact role (not shared directly - decision cannot depend on
 * intelligence.analyst's package-private type, and the two evidence shapes are conceptually
 * distinct: one instrument's history vs. one day's whole cohort).
 */
record ReportFact(String factType, String description) {
}
