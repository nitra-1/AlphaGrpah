package com.alphagraph.ownership.interpretation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One {@code discovered_deals} row, joined to its resolved participant's type/confidence - the
 * raw input to {@link ParticipantFlowAnalyzer}. The caller decides the date range (the
 * 20-calendar-day interpretation window, or {@link DiscoveryConfirmationEngine}'s strictly-post-
 * anchor window) - this type carries no assumption about which.
 */
record ParticipantDealActivity(
    UUID participantId, String canonicalName, ParticipantType participantType, double participantConfidence,
    String buySell, BigDecimal value, LocalDate dealDate
) {
}
