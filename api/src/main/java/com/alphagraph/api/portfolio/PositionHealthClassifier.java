package com.alphagraph.api.portfolio;

import com.alphagraph.decision.api.DecisionRating;
import com.alphagraph.decision.api.DecisionScore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Classifies how a holding's Swing setup has moved since the user's entry - mirrors
 * {@code risk.engine.RiskEngine.classify()}'s "hardcoded threshold ladder in a small static
 * method" idiom rather than a data-driven {@code common.rules.RuleSet}, matching every other
 * classifier in this codebase. Takes two already-computed {@link DecisionScore} rows (entry and
 * current) - it never fetches anything itself and never fabricates a number not already present
 * in either score, same "AI explains, it never calculates" discipline {@code decision.analyst}
 * already follows, just without an LLM in this v1.
 */
@Component
class PositionHealthClassifier {

    /** Same constant {@code decision.analyst.AnalystEvidenceBuilder} uses to decide a domain move is worth mentioning. */
    private static final double MEANINGFUL_SCORE_DELTA = 5.0;
    private static final String FIRST_ENTRY = "FIRST_ENTRY";

    PositionHealthResult classify(DecisionScore entryScore, DecisionScore currentScore) {
        double swingScoreChange = currentScore.swingScore() - entryScore.swingScore();
        PositionHealth positionHealth = classifyHealth(currentScore.swingRating(), swingScoreChange);

        Integer swingRankChange = (entryScore.swingRank() != null && currentScore.swingRank() != null)
            ? entryScore.swingRank() - currentScore.swingRank() : null;

        RankDeterioration rankDeterioration = classifyRankDeterioration(entryScore, currentScore);
        List<DomainDelta> domainDeltas = computeDomainDeltas(entryScore, currentScore);
        HealthReason healthReason = classifyReason(positionHealth, swingScoreChange, rankDeterioration, domainDeltas);
        AttentionLevel attentionLevel = classifyAttention(positionHealth, rankDeterioration);

        return new PositionHealthResult(
            positionHealth, healthReason, attentionLevel,
            entryScore.swingScore(), swingScoreChange,
            entryScore.swingRank(), swingRankChange,
            rankDeterioration == null ? null : rankDeterioration.level(),
            rankDeterioration == null ? null : rankDeterioration.basis(),
            entryScore.asOfDate(), FIRST_ENTRY,
            domainDeltas
        );
    }

    private static PositionHealth classifyHealth(DecisionRating currentRating, double swingScoreChange) {
        if (currentRating == DecisionRating.REDUCE || currentRating == DecisionRating.AVOID) {
            return PositionHealth.SWING_SETUP_BROKEN;
        }
        if (swingScoreChange >= 0) {
            return PositionHealth.STRONG;
        }
        if (swingScoreChange > -MEANINGFUL_SCORE_DELTA) {
            return PositionHealth.STABLE;
        }
        return PositionHealth.WEAKENING;
    }

    private record RankDeterioration(RankDeteriorationLevel level, RankDeteriorationBasis basis) {
    }

    private static RankDeterioration classifyRankDeterioration(DecisionScore entryScore, DecisionScore currentScore) {
        Integer entryRank = entryScore.swingRank();
        Integer currentRank = currentScore.swingRank();
        if (entryRank == null || currentRank == null) {
            return null;
        }

        Integer entryUniverse = entryScore.swingRankUniverseSize();
        Integer currentUniverse = currentScore.swingRankUniverseSize();
        if (entryUniverse != null && entryUniverse > 0 && currentUniverse != null && currentUniverse > 0) {
            double entryRankFraction = entryRank / (double) entryUniverse;
            double currentRankFraction = currentRank / (double) currentUniverse;
            double deterioration = currentRankFraction - entryRankFraction;
            return new RankDeterioration(bandForFraction(deterioration), RankDeteriorationBasis.RANK_FRACTION);
        }

        int placesDropped = currentRank - entryRank;
        return new RankDeterioration(bandForRawPlaces(placesDropped), RankDeteriorationBasis.RAW_RANK_FALLBACK);
    }

    private static RankDeteriorationLevel bandForFraction(double deterioration) {
        if (deterioration < 0.10) return RankDeteriorationLevel.NONE;
        if (deterioration < 0.20) return RankDeteriorationLevel.MILD;
        if (deterioration < 0.35) return RankDeteriorationLevel.MATERIAL;
        return RankDeteriorationLevel.SEVERE;
    }

    private static RankDeteriorationLevel bandForRawPlaces(int placesDropped) {
        if (placesDropped < 5) return RankDeteriorationLevel.NONE;
        if (placesDropped <= 10) return RankDeteriorationLevel.MILD;
        if (placesDropped <= 20) return RankDeteriorationLevel.MATERIAL;
        return RankDeteriorationLevel.SEVERE;
    }

    private static List<DomainDelta> computeDomainDeltas(DecisionScore entryScore, DecisionScore currentScore) {
        List<DomainDelta> deltas = new ArrayList<>();
        addDomainDelta(deltas, "TECHNICAL", entryScore.technicalScore(), currentScore.technicalScore());
        addDomainDelta(deltas, "FUNDAMENTAL", entryScore.fundamentalScore(), currentScore.fundamentalScore());
        addDomainDelta(deltas, "INSTITUTIONAL", entryScore.institutionalScore(), currentScore.institutionalScore());
        addDomainDelta(deltas, "SECTOR", entryScore.sectorScore(), currentScore.sectorScore());
        addDomainDelta(deltas, "RISK", entryScore.riskScore(), currentScore.riskScore());
        addDomainDelta(deltas, "CORPORATE", entryScore.corporateScore(), currentScore.corporateScore());
        return deltas;
    }

    private static void addDomainDelta(List<DomainDelta> deltas, String domain, Double entryValue, Double currentValue) {
        if (entryValue == null || currentValue == null) {
            return;
        }
        double delta = currentValue - entryValue;
        if (Math.abs(delta) < MEANINGFUL_SCORE_DELTA) {
            return;
        }
        deltas.add(new DomainDelta(domain, entryValue, currentValue, delta));
    }

    private static boolean rankFellMaterially(RankDeterioration rankDeterioration) {
        return rankDeterioration != null
            && (rankDeterioration.level() == RankDeteriorationLevel.MATERIAL || rankDeterioration.level() == RankDeteriorationLevel.SEVERE);
    }

    private static HealthReason classifyReason(
        PositionHealth positionHealth, double swingScoreChange, RankDeterioration rankDeterioration, List<DomainDelta> domainDeltas
    ) {
        boolean scoreNotMeaningfullyDown = positionHealth == PositionHealth.STRONG || positionHealth == PositionHealth.STABLE;
        if (scoreNotMeaningfullyDown) {
            return rankFellMaterially(rankDeterioration) ? HealthReason.RELATIVE_RANK_WEAKENING : null;
        }

        // WEAKENING or SWING_SETUP_BROKEN from here - there is a meaningful score decline to attribute.
        List<DomainDelta> declines = domainDeltas.stream().filter(d -> d.delta() < 0).toList();
        if (declines.size() >= 4) {
            return HealthReason.BROAD_BASED_WEAKENING;
        }

        double marketSetupMagnitude = bucketMagnitude(declines, "TECHNICAL", "SECTOR", "INSTITUTIONAL");
        double businessMagnitude = bucketMagnitude(declines, "FUNDAMENTAL", "CORPORATE");
        double riskMagnitude = bucketMagnitude(declines, "RISK");

        if (marketSetupMagnitude == 0 && businessMagnitude == 0 && riskMagnitude == 0) {
            // The composite Swing Score declined meaningfully but no single domain crossed the
            // 5.0-point threshold on its own (e.g. several smaller sub-threshold moves) - too
            // diffuse to attribute to one bucket, so treated the same as a broad decline rather
            // than guessing.
            return HealthReason.BROAD_BASED_WEAKENING;
        }
        if (marketSetupMagnitude >= businessMagnitude && marketSetupMagnitude >= riskMagnitude) {
            return HealthReason.MARKET_SETUP_WEAKENING;
        }
        if (businessMagnitude >= riskMagnitude) {
            return HealthReason.BUSINESS_QUALITY_WEAKENING;
        }
        return HealthReason.RISK_DETERIORATION;
    }

    private static double bucketMagnitude(List<DomainDelta> declines, String... domains) {
        double total = 0;
        for (DomainDelta delta : declines) {
            for (String domain : domains) {
                if (delta.domain().equals(domain)) {
                    total += Math.abs(delta.delta());
                }
            }
        }
        return total;
    }

    private static AttentionLevel classifyAttention(PositionHealth positionHealth, RankDeterioration rankDeterioration) {
        if (positionHealth == PositionHealth.SWING_SETUP_BROKEN) {
            return AttentionLevel.HIGH;
        }
        if (positionHealth == PositionHealth.WEAKENING) {
            return rankFellMaterially(rankDeterioration) ? AttentionLevel.HIGH : AttentionLevel.MEDIUM;
        }
        if (positionHealth == PositionHealth.STABLE && rankDeterioration != null && rankDeterioration.level() == RankDeteriorationLevel.SEVERE) {
            return AttentionLevel.MEDIUM;
        }
        return AttentionLevel.LOW;
    }
}
