package com.alphagraph.market.transformation;

import java.time.LocalDate;
import java.util.Set;

/**
 * One real trading day's full reason-code set from {@code market.inflection_states}/
 * {@code _state_reasons}, plus each of the 3 metrics' own {@link MarketEvidenceObservation} for
 * that day (may be {@code null} per metric if that metric had no real evidence that specific day -
 * rare, but the merge is date-based, not assumed 1:1). The metric fields exist so Stage 3 can trace
 * a detected reason code back to its *own* underlying metric's real confidence/persistence,
 * never the day's winning {@code primary_state}'s driving-metric numbers (see
 * {@code MarketTransformationSequenceEngine}'s javadoc for why that distinction matters).
 */
record MarketInflectionHistoryEntry(
    LocalDate asOfDate, String symbol, Set<String> reasonCodes,
    MarketEvidenceObservation delivery, MarketEvidenceObservation relativeVolume, MarketEvidenceObservation priceReturn
) {

    boolean hasReason(String code) {
        return reasonCodes.contains(code);
    }
}
