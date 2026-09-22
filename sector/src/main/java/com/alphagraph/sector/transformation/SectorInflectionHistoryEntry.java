package com.alphagraph.sector.transformation;

import java.time.LocalDate;
import java.util.Set;

/**
 * One real Sector Stage 2 row's full reason-code set from {@code sector.inflection_states}/
 * {@code _state_reasons}, plus {@code SECTOR_RELATIVE_STRENGTH}/{@code VS_NIFTY}/{@code VS_SECTOR}'s
 * own {@link SectorEvidenceObservation} as-of-merged onto this row's {@code asOfDate} (may be
 * {@code null} per metric if no real evidence exists at or before this date). The 3 metric fields
 * are kept as raw observations, not just resolved reason values - {@code IDIOSYNCRATIC_LEADERSHIP}'s
 * freshness check needs direct access to each metric's own {@code asOfDate}/{@code value}/
 * {@code change}, not merely whether a reason code fired.
 */
record SectorInflectionHistoryEntry(
    LocalDate asOfDate, String symbol, Set<String> reasonCodes,
    SectorEvidenceObservation sectorRelativeStrength, SectorEvidenceObservation vsNifty, SectorEvidenceObservation vsSector
) {

    boolean hasReason(String code) {
        return reasonCodes.contains(code);
    }
}
