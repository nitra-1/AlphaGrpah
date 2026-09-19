package com.alphagraph.sector.transformation;

/**
 * Matches {@code sector.transformation_evidence.metric_name}'s CHECK constraint exactly. A local
 * copy, not a reuse of {@code intelligence.sectorcontext.SectorContextMetric} (package-private,
 * different module) - same "domain packages never share a type across module-internal boundaries"
 * convention {@code market}/{@code financial}/{@code corporate} each already follow.
 */
enum SectorMetric {
    SECTOR_RELATIVE_STRENGTH,
    INSTRUMENT_RELATIVE_STRENGTH_VS_NIFTY,
    INSTRUMENT_RELATIVE_STRENGTH_VS_SECTOR
}
