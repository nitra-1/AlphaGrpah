package com.alphagraph.ownership.interpretation;

import java.math.BigDecimal;
import java.util.UUID;

/** One participant's own buy/sell/churn shape within the window {@link ParticipantFlowAnalyzer} was given. */
record ParticipantFlow(
    UUID participantId, String canonicalName, ParticipantType participantType, double participantConfidence,
    BigDecimal buyValue, BigDecimal sellValue, BigDecimal matchedRoundTripValue,
    double churnRatio, ChurnState churnState, int buySessions, int sellSessions,
    RepeatBehavior repeatBehavior
) {
}
