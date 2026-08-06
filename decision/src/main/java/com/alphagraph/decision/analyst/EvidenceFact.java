package com.alphagraph.decision.analyst;

/**
 * One already-verified, already-worded fact about an instrument - every number in
 * {@code description} was computed and substituted in by Java, not by the LLM. This is what makes
 * "AI explains, it never calculates" enforceable: {@link AiAnalystClient} only ever sees a list of
 * these, and its prompt forbids introducing any number that isn't already present in one.
 */
record EvidenceFact(String factType, String description) {
}
