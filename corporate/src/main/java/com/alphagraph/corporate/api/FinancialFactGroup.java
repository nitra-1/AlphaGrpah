package com.alphagraph.corporate.api;

import java.util.List;
import java.util.UUID;

/**
 * One period's worth of {@code corporate.knowledge.FinancialResultsExtractor} output - every
 * {@link DocumentFact} sharing one {@code fact_group}, alongside the parent document's instrument.
 * Read-only bridging shape: {@code corporate} cannot depend on {@code financial}
 * (docs/001_System_Architecture.md §4), so {@code intelligence.financial.FinancialResultsBridgeOrchestrator}
 * is what actually maps this into {@code financial.api.FinancialResult} and writes it - this
 * module only publishes the raw grouped facts.
 */
public record FinancialFactGroup(UUID documentId, UUID instrumentId, String symbol, List<DocumentFact> facts) {
}
