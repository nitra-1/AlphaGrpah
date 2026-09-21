package com.alphagraph.intelligence.riskcontradiction;

import java.time.LocalDate;
import java.util.List;

/**
 * One family's latest real Stage 2 state row, reduced to exactly what this meta-layer needs: when
 * it was computed, how confident that family already is in it (its own real stored value, never a
 * fixed bucket here), and which reason codes it actually recorded - regardless of which
 * {@code primary_state} won that family's own priority ladder (see
 * {@code RiskContradictionEngine}'s javadoc for why sub-conditions are checked via reason codes,
 * not {@code primary_state}). Shared uniformly across all 4 source families, including Ownership -
 * verified against the real {@code OwnershipTransformationEngine} source that its own
 * {@code OWNERSHIP_CONTRADICTION} reason code is added if and only if it wins (it's first in that
 * engine's own priority ladder), so a reason-code check is provably equivalent to a
 * {@code primary_state} check there today - reason-code is still used uniformly for consistency
 * and defensiveness against a future reordering of a ladder this module doesn't own.
 */
record FamilyStateSignal(LocalDate asOfDate, double confidence, List<ReasonCode> reasons) {

    boolean hasReason(String code) {
        return reasons.stream().anyMatch(r -> r.code().equals(code));
    }
}
