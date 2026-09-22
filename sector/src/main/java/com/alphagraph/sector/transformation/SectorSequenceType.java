package com.alphagraph.sector.transformation;

/**
 * Matches {@code sector.transformation_sequences.sequence_type}'s CHECK constraint exactly. An
 * instrument may legitimately have multiple of these active simultaneously - no priority ladder.
 */
enum SectorSequenceType {
    SECTOR_TAILWIND_SEQUENCE,
    STOCK_LEADERSHIP_EMERGENCE,
    IDIOSYNCRATIC_LEADERSHIP
}
