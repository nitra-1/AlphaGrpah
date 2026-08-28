package com.alphagraph.ownership.deals;

import java.math.BigDecimal;

/**
 * The 20-calendar-day-window inputs {@link BulkDealContextReader} resolves for one deal being
 * scored - repetition/breadth already resolved to the deal's own {@code buy_sell} side (see
 * {@link BulkDealContextReader}'s doc comment on the direction-neutral fix), plus both-side
 * evidence counts and reported buy/sell totals kept separately for {@link DealMaterialityEngine}'s
 * genuinely distinct reported-net-flow signal.
 */
record BulkDealContext(
    int sameSideClientDealCount20CalendarDays, int distinctSameSideClients20CalendarDays,
    int distinctBuyers20CalendarDays, int distinctSellers20CalendarDays,
    BigDecimal reportedBuyValue20CalendarDays, BigDecimal reportedSellValue20CalendarDays
) {
}
