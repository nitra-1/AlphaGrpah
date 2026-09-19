package com.alphagraph.sector.transformation;

/**
 * Matches {@code sector.inflection_states.primary_state}'s CHECK constraint exactly. Not mutually
 * exclusive in what actually "fires" - {@link SectorInflectionEngine} picks one of these as
 * {@code primaryState} via a hardcoded priority ladder, but every state that fired is still
 * recorded as a reason code, same convention every other family's Stage 2 already uses.
 */
enum SectorInflectionState {
    NO_CLEAR_SIGNAL,
    SECTOR_STRENGTHENING,
    STOCK_OUTPERFORMING_NIFTY,
    STOCK_OUTPERFORMING_SECTOR,
    NEW_LEADERSHIP_EMERGENCE
}
