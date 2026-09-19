package com.alphagraph.corporate.transformation;

/**
 * Matches {@code corporate.inflection_states.primary_state}'s CHECK constraint exactly.
 * {@code MIXED_CAPITAL_ALLOCATION_ACTIVITY} exists so a real co-occurring buyback + equity-raise
 * in the same 180-day window is its own honest historical record, not silently collapsed into
 * whichever of {@code BUYBACK_ACTIVITY}/{@code EQUITY_RAISE_ACTIVITY} an arbitrary tie-break picked
 * - both component reason codes are still recorded regardless of which state wins, same convention
 * every other family's Stage 2 already uses.
 */
enum CapitalAllocationInflectionState {
    NO_CLEAR_SIGNAL,
    BUYBACK_ACTIVITY,
    EQUITY_RAISE_ACTIVITY,
    MIXED_CAPITAL_ALLOCATION_ACTIVITY
}
