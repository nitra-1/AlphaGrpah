package com.alphagraph.api.portfolio;

import java.time.LocalDate;
import java.util.List;

/** Everything {@link PositionHealthClassifier} knows about a holding's health since entry. */
record PositionHealthResult(
    PositionHealth positionHealth, HealthReason healthReason, AttentionLevel attentionLevel,
    double entrySwingScore, double swingScoreChange,
    Integer entrySwingRank, Integer swingRankChange,
    RankDeteriorationLevel rankDeteriorationLevel, RankDeteriorationBasis rankDeteriorationBasis,
    LocalDate healthAnchorDate, String healthAnchorType,
    List<DomainDelta> domainDeltas
) {
}
