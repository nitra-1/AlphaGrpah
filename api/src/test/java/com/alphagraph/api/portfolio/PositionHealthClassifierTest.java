package com.alphagraph.api.portfolio;

import com.alphagraph.decision.api.DecisionRating;
import com.alphagraph.decision.api.DecisionScore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PositionHealthClassifierTest {

    private final PositionHealthClassifier classifier = new PositionHealthClassifier();
    private final UUID instrumentId = UUID.randomUUID();
    private final LocalDate entryDate = LocalDate.of(2026, 8, 13);
    private final LocalDate currentDate = LocalDate.of(2026, 8, 15);

    // --- positionHealth bands ---

    @Test
    void strongWhenCurrentScoreIsAtOrAboveEntry() {
        DecisionScore entry = score(entryDate, 60.0, DecisionRating.BUY, 10, 55, null, null, null, null, null, null);
        DecisionScore current = score(currentDate, 65.0, DecisionRating.BUY, 8, 55, null, null, null, null, null, null);

        PositionHealthResult result = classifier.classify(entry, current);

        assertThat(result.positionHealth()).isEqualTo(PositionHealth.STRONG);
        assertThat(result.swingScoreChange()).isEqualTo(5.0);
    }

    @Test
    void stableWhenDeclineIsSmallerThanTheMeaningfulThreshold() {
        DecisionScore entry = score(entryDate, 60.0, DecisionRating.BUY, 10, 55, null, null, null, null, null, null);
        DecisionScore current = score(currentDate, 57.0, DecisionRating.BUY, 12, 55, null, null, null, null, null, null);

        PositionHealthResult result = classifier.classify(entry, current);

        assertThat(result.positionHealth()).isEqualTo(PositionHealth.STABLE);
    }

    @Test
    void weakeningWhenDeclineIsMeaningfulButRatingHoldsUp() {
        DecisionScore entry = score(entryDate, 87.0, DecisionRating.STRONG_BUY, 1, 55, 92.0, null, null, null, null, null);
        DecisionScore current = score(currentDate, 74.0, DecisionRating.BUY, 17, 55, 73.0, null, null, null, null, null);

        PositionHealthResult result = classifier.classify(entry, current);

        assertThat(result.positionHealth()).isEqualTo(PositionHealth.WEAKENING);
        assertThat(result.swingScoreChange()).isEqualTo(-13.0);
    }

    @Test
    void swingSetupBrokenWhenCurrentRatingIsReduceOrAvoidRegardlessOfDeltaSize() {
        // Only a 2-point drop, but the rating has already fallen into REDUCE - the absolute
        // signal must win over the small raw delta.
        DecisionScore entry = score(entryDate, 42.0, DecisionRating.HOLD, 20, 55, null, null, null, null, null, null);
        DecisionScore current = score(currentDate, 40.0, DecisionRating.REDUCE, 25, 55, null, null, null, null, null, null);

        PositionHealthResult result = classifier.classify(entry, current);

        assertThat(result.positionHealth()).isEqualTo(PositionHealth.SWING_SETUP_BROKEN);
    }

    @Test
    void swingSetupBrokenDoesNotClaimTheBusinessThesisIsInvalid() {
        // Strong Fundamental/Corporate, weak Technical/Sector - a real case where the Swing setup
        // breaks down without the business thesis being touched at all.
        DecisionScore entry = score(entryDate, 70.0, DecisionRating.BUY, 5, 55, 80.0, 91.0, null, 82.0, null, 88.0);
        DecisionScore current = score(currentDate, 55.0, DecisionRating.REDUCE, 30, 55, 38.0, 91.0, null, 44.0, null, 88.0);

        PositionHealthResult result = classifier.classify(entry, current);

        assertThat(result.positionHealth()).isEqualTo(PositionHealth.SWING_SETUP_BROKEN);
        assertThat(result.healthReason()).isEqualTo(HealthReason.MARKET_SETUP_WEAKENING);
    }

    // --- healthReason ---

    @Test
    void marketSetupWeakeningWhenTechnicalAndSectorDominateTheDecline() {
        DecisionScore entry = score(entryDate, 87.0, DecisionRating.STRONG_BUY, 1, 55, 92.0, 86.0, 84.0, 86.0, 91.0, 77.0);
        DecisionScore current = score(currentDate, 74.0, DecisionRating.BUY, 17, 55, 73.0, 86.0, 84.0, 78.0, 91.0, 77.0);

        PositionHealthResult result = classifier.classify(entry, current);

        assertThat(result.healthReason()).isEqualTo(HealthReason.MARKET_SETUP_WEAKENING);
        assertThat(result.domainDeltas()).extracting(DomainDelta::domain).containsExactlyInAnyOrder("TECHNICAL", "SECTOR");
    }

    @Test
    void businessQualityWeakeningWhenFundamentalAndCorporateDominateTheDecline() {
        DecisionScore entry = score(entryDate, 75.0, DecisionRating.BUY, 5, 55, 80.0, 88.0, 80.0, 80.0, 85.0, 82.0);
        DecisionScore current = score(currentDate, 60.0, DecisionRating.HOLD, 20, 55, 80.0, 70.0, 80.0, 80.0, 85.0, 65.0);

        PositionHealthResult result = classifier.classify(entry, current);

        assertThat(result.healthReason()).isEqualTo(HealthReason.BUSINESS_QUALITY_WEAKENING);
    }

    @Test
    void riskDeteriorationWhenRiskAloneDrivesTheDecline() {
        DecisionScore entry = score(entryDate, 75.0, DecisionRating.BUY, 5, 55, 80.0, 80.0, 80.0, 80.0, 90.0, 80.0);
        DecisionScore current = score(currentDate, 68.0, DecisionRating.BUY, 8, 55, 80.0, 80.0, 80.0, 80.0, 60.0, 80.0);

        PositionHealthResult result = classifier.classify(entry, current);

        assertThat(result.healthReason()).isEqualTo(HealthReason.RISK_DETERIORATION);
    }

    @Test
    void broadBasedWeakeningWhenFourOrMoreDomainsDeclineMeaningfully() {
        DecisionScore entry = score(entryDate, 80.0, DecisionRating.BUY, 5, 55, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0);
        DecisionScore current = score(currentDate, 65.0, DecisionRating.HOLD, 20, 55, 70.0, 70.0, 70.0, 70.0, 80.0, 80.0);

        PositionHealthResult result = classifier.classify(entry, current);

        assertThat(result.healthReason()).isEqualTo(HealthReason.BROAD_BASED_WEAKENING);
    }

    @Test
    void relativeRankWeakeningWhenScoreBarelyMovedButRankFellMaterially() {
        // The user's own "16 other stocks got stronger" case - HCLTECH itself didn't really get worse.
        DecisionScore entry = score(entryDate, 80.0, DecisionRating.BUY, 1, 20, null, null, null, null, null, null);
        DecisionScore current = score(currentDate, 79.0, DecisionRating.BUY, 9, 20, null, null, null, null, null, null);

        PositionHealthResult result = classifier.classify(entry, current);

        assertThat(result.positionHealth()).isEqualTo(PositionHealth.STABLE);
        assertThat(result.healthReason()).isEqualTo(HealthReason.RELATIVE_RANK_WEAKENING);
    }

    @Test
    void healthReasonIsNullWhenThereIsNothingMeaningfulToExplain() {
        DecisionScore entry = score(entryDate, 80.0, DecisionRating.BUY, 5, 55, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0);
        DecisionScore current = score(currentDate, 78.0, DecisionRating.BUY, 6, 55, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0);

        PositionHealthResult result = classifier.classify(entry, current);

        assertThat(result.positionHealth()).isEqualTo(PositionHealth.STABLE);
        assertThat(result.healthReason()).isNull();
        assertThat(result.domainDeltas()).isEmpty();
    }

    // --- rankDeteriorationLevel / rankDeteriorationBasis (rank-fraction path) ---

    @Test
    void rankFractionDeteriorationMatchesTheRealHclitechExample() {
        // #1 of 55 -> #17 of 55: fraction moves ~0.018 -> ~0.309, a ~0.29 deterioration -> MATERIAL.
        DecisionScore entry = score(entryDate, 87.0, DecisionRating.STRONG_BUY, 1, 55, null, null, null, null, null, null);
        DecisionScore current = score(currentDate, 74.0, DecisionRating.BUY, 17, 55, null, null, null, null, null, null);

        PositionHealthResult result = classifier.classify(entry, current);

        assertThat(result.rankDeteriorationBasis()).isEqualTo(RankDeteriorationBasis.RANK_FRACTION);
        assertThat(result.rankDeteriorationLevel()).isEqualTo(RankDeteriorationLevel.MATERIAL);
        assertThat(result.swingRankChange()).isEqualTo(-16);
    }

    @Test
    void rankFractionLevelIsNoneWhenRankBarelyMoved() {
        DecisionScore entry = score(entryDate, 80.0, DecisionRating.BUY, 10, 100, null, null, null, null, null, null);
        DecisionScore current = score(currentDate, 79.0, DecisionRating.BUY, 12, 100, null, null, null, null, null, null);

        PositionHealthResult result = classifier.classify(entry, current);

        assertThat(result.rankDeteriorationLevel()).isEqualTo(RankDeteriorationLevel.NONE);
    }

    @Test
    void rankFractionLevelIsMildBetweenTenAndTwentyPercent() {
        DecisionScore entry = score(entryDate, 80.0, DecisionRating.BUY, 1, 100, null, null, null, null, null, null);
        DecisionScore current = score(currentDate, 79.0, DecisionRating.BUY, 16, 100, null, null, null, null, null, null);

        PositionHealthResult result = classifier.classify(entry, current);

        assertThat(result.rankDeteriorationLevel()).isEqualTo(RankDeteriorationLevel.MILD);
    }

    @Test
    void rankFractionLevelIsSevereAboveThirtyFivePercent() {
        DecisionScore entry = score(entryDate, 80.0, DecisionRating.BUY, 1, 100, null, null, null, null, null, null);
        DecisionScore current = score(currentDate, 79.0, DecisionRating.BUY, 40, 100, null, null, null, null, null, null);

        PositionHealthResult result = classifier.classify(entry, current);

        assertThat(result.rankDeteriorationLevel()).isEqualTo(RankDeteriorationLevel.SEVERE);
    }

    // --- rankDeteriorationLevel fallback (raw places, missing universe size) ---

    @Test
    void fallsBackToRawPlacesWhenUniverseSizeIsMissing() {
        DecisionScore entry = score(entryDate, 80.0, DecisionRating.BUY, 1, null, null, null, null, null, null, null);
        DecisionScore current = score(currentDate, 79.0, DecisionRating.BUY, 15, null, null, null, null, null, null, null);

        PositionHealthResult result = classifier.classify(entry, current);

        assertThat(result.rankDeteriorationBasis()).isEqualTo(RankDeteriorationBasis.RAW_RANK_FALLBACK);
        assertThat(result.rankDeteriorationLevel()).isEqualTo(RankDeteriorationLevel.MATERIAL);
    }

    @Test
    void rawPlacesFallbackBandsMatchTheOriginalLadder() {
        assertThat(rawFallbackLevel(3)).isEqualTo(RankDeteriorationLevel.NONE);
        assertThat(rawFallbackLevel(7)).isEqualTo(RankDeteriorationLevel.MILD);
        assertThat(rawFallbackLevel(15)).isEqualTo(RankDeteriorationLevel.MATERIAL);
        assertThat(rawFallbackLevel(25)).isEqualTo(RankDeteriorationLevel.SEVERE);
    }

    private RankDeteriorationLevel rawFallbackLevel(int placesDropped) {
        DecisionScore entry = score(entryDate, 80.0, DecisionRating.BUY, 1, null, null, null, null, null, null, null);
        DecisionScore current = score(currentDate, 79.0, DecisionRating.BUY, 1 + placesDropped, null, null, null, null, null, null, null);
        return classifier.classify(entry, current).rankDeteriorationLevel();
    }

    // --- attentionLevel combination matrix ---

    @Test
    void attentionIsHighWhenSwingSetupIsBroken() {
        DecisionScore entry = score(entryDate, 60.0, DecisionRating.HOLD, 20, 55, null, null, null, null, null, null);
        DecisionScore current = score(currentDate, 58.0, DecisionRating.REDUCE, 25, 55, null, null, null, null, null, null);

        assertThat(classifier.classify(entry, current).attentionLevel()).isEqualTo(AttentionLevel.HIGH);
    }

    @Test
    void attentionIsHighWhenWeakeningCombinesWithMaterialRankFall() {
        DecisionScore entry = score(entryDate, 87.0, DecisionRating.STRONG_BUY, 1, 55, null, null, null, null, null, null);
        DecisionScore current = score(currentDate, 74.0, DecisionRating.BUY, 17, 55, null, null, null, null, null, null);

        assertThat(classifier.classify(entry, current).attentionLevel()).isEqualTo(AttentionLevel.HIGH);
    }

    @Test
    void attentionIsMediumWhenWeakeningButRankHoldsUp() {
        DecisionScore entry = score(entryDate, 80.0, DecisionRating.BUY, 5, 55, null, null, null, null, null, null);
        DecisionScore current = score(currentDate, 70.0, DecisionRating.BUY, 6, 55, null, null, null, null, null, null);

        assertThat(classifier.classify(entry, current).attentionLevel()).isEqualTo(AttentionLevel.MEDIUM);
    }

    @Test
    void attentionIsMediumWhenStableButRankFallsSeverely() {
        DecisionScore entry = score(entryDate, 80.0, DecisionRating.BUY, 1, 100, null, null, null, null, null, null);
        DecisionScore current = score(currentDate, 79.0, DecisionRating.BUY, 40, 100, null, null, null, null, null, null);

        assertThat(classifier.classify(entry, current).attentionLevel()).isEqualTo(AttentionLevel.MEDIUM);
    }

    @Test
    void attentionIsLowWhenStrong() {
        DecisionScore entry = score(entryDate, 60.0, DecisionRating.BUY, 10, 55, null, null, null, null, null, null);
        DecisionScore current = score(currentDate, 65.0, DecisionRating.BUY, 8, 55, null, null, null, null, null, null);

        assertThat(classifier.classify(entry, current).attentionLevel()).isEqualTo(AttentionLevel.LOW);
    }

    // --- anchor transparency ---

    @Test
    void healthAnchorIsTheEntryScoresRealAsOfDateAndTypeIsFirstEntry() {
        DecisionScore entry = score(entryDate, 60.0, DecisionRating.BUY, 10, 55, null, null, null, null, null, null);
        DecisionScore current = score(currentDate, 65.0, DecisionRating.BUY, 8, 55, null, null, null, null, null, null);

        PositionHealthResult result = classifier.classify(entry, current);

        assertThat(result.healthAnchorDate()).isEqualTo(entryDate);
        assertThat(result.healthAnchorType()).isEqualTo("FIRST_ENTRY");
    }

    private DecisionScore score(
        LocalDate asOfDate, double swingScore, DecisionRating swingRating, Integer swingRank, Integer universeSize,
        Double technical, Double fundamental, Double institutional, Double sector, Double risk, Double corporate
    ) {
        return new DecisionScore(
            instrumentId, "TEST", asOfDate,
            swingScore, swingRating, swingRank,
            60.0, DecisionRating.HOLD, 1,
            technical, fundamental, institutional, sector, risk, corporate,
            80.0, 1, Instant.now(),
            null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null,
            null, universeSize, universeSize
        );
    }
}
