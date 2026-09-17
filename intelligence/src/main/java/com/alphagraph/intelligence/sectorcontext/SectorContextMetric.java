package com.alphagraph.intelligence.sectorcontext;

enum SectorContextMetric {
    /** Instrument's own rolling 20-day return minus NIFTY50's. */
    INSTRUMENT_RELATIVE_STRENGTH_VS_NIFTY,
    /** Instrument's own rolling 20-day return minus its sector's verified benchmark index's. */
    INSTRUMENT_RELATIVE_STRENGTH_VS_SECTOR,
    /** Re-persist of sector.sector_scores.relative_strength (sector vs. market), attributed to this instrument - no new math. */
    SECTOR_RELATIVE_STRENGTH
}
