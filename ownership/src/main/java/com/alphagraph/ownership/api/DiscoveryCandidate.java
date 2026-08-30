package com.alphagraph.ownership.api;

import java.time.LocalDate;

/**
 * One untracked symbol with real bulk/block deal activity - aggregated at read time from
 * {@code ownership.discovered_deals} for the admin Discovery review page. {@code securityName}
 * may be null (display only, never guaranteed - see {@code ownership.deals.RawDealRow}).
 * {@code distinctBuyers}/{@code distinctSellers} are each filtered to their own side - a real,
 * pre-Sprint-3 bug had "distinct buyers" counting all participants regardless of side. Every
 * aggregate here excludes a deal's cross-feed BULK/BLOCK duplicate (see
 * {@code ownership.deals.DiscoveredDealWriter}) so a genuinely single real trade is never summed
 * twice.
 *
 * <p>{@code maxMaterialityScore}/{@code maxMaterialityLevel} and {@code largestDealToAdtvRatio}
 * are computed independently (Sprint 2) - deliberately not from the same deal: the deal with the
 * highest blended materiality score isn't necessarily the deal with the highest raw ratio (e.g. a
 * high-ratio deal with zero repetition/breadth can score lower overall than a merely-moderate-
 * ratio deal repeated by several distinct participants). All three are null when no deal for this
 * symbol has been scored yet (e.g. fewer than 20 trading sessions of price history so far - see
 * {@code ownership.deals.MarketLiquidityReader}), never a guessed placeholder.
 *
 * <p>Sprint 3: {@code eventStructure}/{@code institutionalState}/{@code discoveryConfirmationState}/
 * {@code interpretationConfidence}/{@code churnState}/{@code confirmationSessionsElapsed}/
 * {@code confirmationFrozen}/{@code interpretationReadiness} come from the latest
 * {@code ownership.institutional_interpretations} row - all null (or 0/false for the primitives)
 * until this symbol's first interpretation runs. {@code discoveryConfirmationState} is
 * {@code NOT_APPLICABLE} (not null) for every non-directional {@code institutionalState} - there's
 * nothing directional to confirm. {@code interpretationReadiness} is {@code PENDING_DATA} when any
 * deal in the interpretation window hasn't been materiality-scored yet - so a state like
 * {@code NO_CLEAR_SIGNAL} never silently masquerades as a confident final answer when it's really
 * still waiting on upstream Sprint 2 data.
 */
public record DiscoveryCandidate(
    String symbol, String securityName, int dealCount, int distinctBuyers, int distinctSellers,
    long totalQuantity, LocalDate firstDealDate, LocalDate latestDealDate,
    Double maxMaterialityScore, String maxMaterialityLevel, Double largestDealToAdtvRatio,
    String eventStructure, String institutionalState, String discoveryConfirmationState,
    Double interpretationConfidence, String churnState, int confirmationSessionsElapsed,
    boolean confirmationFrozen, String interpretationReadiness
) {
}
