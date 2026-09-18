package com.alphagraph.common.inflection;

/**
 * Terminal categorical banding of a Stage 1 velocity number - hardcoded Java, never a DB rule,
 * matching every other terminal banding in this codebase ({@code DealMaterialityEngine#materialityLevelFor},
 * {@code CorporateSignalEngine#ratingFor}, the {@code primary_state} priority ladders). A metric
 * that simply didn't move is {@link #FLAT}, not {@link #NEGATIVE} - "negative velocity" for zero
 * change would be a semantically odd output.
 */
public enum VelocityBand {
    STRONG,
    MODERATE,
    WEAK,
    FLAT,
    NEGATIVE
}
