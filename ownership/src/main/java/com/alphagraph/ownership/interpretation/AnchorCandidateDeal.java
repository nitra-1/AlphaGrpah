package com.alphagraph.ownership.interpretation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One {@code discovered_deals} row in the 20-calendar-day interpretation window, carrying its own
 * per-deal materiality level - the input {@link ConfirmationAnchorResolver} decides
 * {@code event_anchor_date} from, and {@link DiscoveryConfirmationEngine} uses (filtered to the
 * anchor date and the confirming side) to compute the directionally-pure weighted event price.
 */
record AnchorCandidateDeal(
    UUID participantId, LocalDate dealDate, String buySell, MaterialityLevel materialityLevel,
    BigDecimal quantity, BigDecimal price, BigDecimal value
) {
}
