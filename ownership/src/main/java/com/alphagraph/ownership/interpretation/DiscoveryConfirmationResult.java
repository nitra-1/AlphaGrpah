package com.alphagraph.ownership.interpretation;

import java.math.BigDecimal;
import java.time.LocalDate;

/** {@link DiscoveryConfirmationEngine}'s output. Every score field is null for {@code NOT_APPLICABLE}. */
record DiscoveryConfirmationResult(
    DiscoveryConfirmationState state, boolean frozen, LocalDate anchorDate, int sessionsElapsed,
    BigDecimal confirmationScore, BigDecimal priceScore, BigDecimal deliveryScore,
    BigDecimal volumeScore, BigDecimal repeatScore, BigDecimal coveragePct
) {
    static DiscoveryConfirmationResult notApplicable() {
        return new DiscoveryConfirmationResult(
            DiscoveryConfirmationState.NOT_APPLICABLE, true, null, 0,
            null, null, null, null, null, null
        );
    }
}
