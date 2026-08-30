package com.alphagraph.ownership.interpretation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One {@code discovered_deals} row joined to its resolved participant and (if scored yet) its
 * Sprint 2 materiality level - {@link InterpretationDealReader}'s raw output, mapped by the
 * orchestrator into whichever narrower shape each component needs
 * ({@link AnchorCandidateDeal}/{@link ParticipantDealActivity}).
 */
record WindowDealRow(
    UUID participantId, String canonicalName, ParticipantType participantType, double participantConfidence,
    LocalDate dealDate, String buySell, BigDecimal quantity, BigDecimal price, BigDecimal value,
    MaterialityLevel materialityLevel, Double materialityScore, String reportedFlowState
) {
}
