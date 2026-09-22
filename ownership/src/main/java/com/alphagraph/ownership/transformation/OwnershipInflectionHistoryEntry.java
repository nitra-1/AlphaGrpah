package com.alphagraph.ownership.transformation;

import java.time.LocalDate;
import java.util.Set;

/**
 * One distinct reporting period's (quarter's) full reason-code set from
 * {@code ownership.transformation_states}/{@code _state_reasons}, plus the PROMOTER/FII/DII
 * {@link OwnershipEvidenceObservation}s for that quarter and the row's own stored
 * {@code driving_metric} (needed to resolve composite-state evidence correctly - see
 * {@code OwnershipTransformationSequenceEngine}'s javadoc, Correction 3). {@code asOfDate} is the
 * real information-availability date of whichever Stage 2 row was chosen to represent this quarter
 * (see {@link OwnershipInflectionHistoryReader}'s canonicalization); {@code latestPeriodEnd} is the
 * quarter itself - the axis every gap/persistence/contradiction/age count actually walks (Correction 1).
 */
record OwnershipInflectionHistoryEntry(
    LocalDate asOfDate, LocalDate latestPeriodEnd, String symbol, Set<String> reasonCodes, TransformationMetric drivingMetric,
    OwnershipEvidenceObservation promoter, OwnershipEvidenceObservation fii, OwnershipEvidenceObservation dii
) {

    boolean hasReason(String code) {
        return reasonCodes.contains(code);
    }
}
