package com.alphagraph.sector.transformation;

/**
 * Distinct from {@code SectorInflectionState} - reports how many of the 3 source metrics
 * ({@code SECTOR_RELATIVE_STRENGTH}, {@code INSTRUMENT_RELATIVE_STRENGTH_VS_NIFTY},
 * {@code INSTRUMENT_RELATIVE_STRENGTH_VS_SECTOR}) have any real evidence at all for an instrument,
 * independent of whether a state actually fired. Exists so {@code NO_CLEAR_SIGNAL} stays honestly
 * distinguishable between "all 3 sources are real and calm" ({@code READY}) and "2 of 3 sources
 * don't have real evidence yet" ({@code PARTIAL_DATA}/{@code INSUFFICIENT_DATA}) - real and common
 * today, since {@code INSTRUMENT_RELATIVE_STRENGTH_VS_SECTOR} has zero rows system-wide until
 * {@code reference.sector_benchmarks} is populated.
 */
enum SectorDataReadiness {
    READY,
    PARTIAL_DATA,
    INSUFFICIENT_DATA
}
