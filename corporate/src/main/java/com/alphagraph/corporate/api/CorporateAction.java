package com.alphagraph.corporate.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One corporate action for one instrument, mirroring {@code corporate.corporate_actions}
 * (docs/003_Database_Architecture.md §3a). Only {@code instrumentId}, {@code actionType}, and
 * {@code exDate} are guaranteed present - those apply regardless of action type.
 * {@code dividendAmount} only applies to DIVIDEND; {@code ratioNumerator}/{@code ratioDenominator}
 * only to BONUS/SPLIT/RIGHTS; {@code price} only to RIGHTS/BUYBACK - all nullable accordingly.
 */
public record CorporateAction(
    UUID instrumentId, String symbol, String actionType, LocalDate exDate,
    LocalDate recordDate, LocalDate announcementDate,
    BigDecimal dividendAmount, Integer ratioNumerator, Integer ratioDenominator, BigDecimal price
) {
}
