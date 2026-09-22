package com.alphagraph.ownership.transformation;

/**
 * Matches {@code ownership.transformation_sequences.sequence_type}'s CHECK constraint exactly.
 * A stock may legitimately have multiple of these active simultaneously - Stage 3 never picks one
 * "primary" sequence (docs/008 §8/§49), so no priority ladder exists here.
 */
enum OwnershipSequenceType {
    INSTITUTIONAL_OWNERSHIP_BUILDING,
    BROAD_INSTITUTIONAL_PARTICIPATION,
    PROMOTER_INSTITUTION_ALIGNMENT
}
