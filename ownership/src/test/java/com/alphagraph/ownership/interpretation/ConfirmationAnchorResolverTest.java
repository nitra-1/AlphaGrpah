package com.alphagraph.ownership.interpretation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConfirmationAnchorResolverTest {

    private final ConfirmationAnchorResolver resolver = new ConfirmationAnchorResolver();

    private static AnchorCandidateDeal deal(LocalDate date, String buySell, MaterialityLevel level) {
        return new AnchorCandidateDeal(UUID.randomUUID(), date, buySell, level, new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("5000"));
    }

    @Test
    void nonDirectionalStateNeverHasAnAnchor() {
        Optional<LocalDate> result = resolver.resolve(
            InstitutionalState.HIGH_CHURN, null, null, List.of(deal(LocalDate.of(2026, 8, 26), "BUY", MaterialityLevel.HIGH))
        );

        assertThat(result).isEmpty();
    }

    @Test
    void firstEverInterpretationAnchorsToTheLatestMediumPlusSameDirectionDeal() {
        List<AnchorCandidateDeal> deals = List.of(
            deal(LocalDate.of(2026, 8, 24), "BUY", MaterialityLevel.HIGH),
            deal(LocalDate.of(2026, 8, 26), "BUY", MaterialityLevel.MEDIUM),
            deal(LocalDate.of(2026, 8, 27), "BUY", MaterialityLevel.LOW)
        );

        Optional<LocalDate> result = resolver.resolve(InstitutionalState.POSSIBLE_ACCUMULATION, null, null, deals);

        assertThat(result).contains(LocalDate.of(2026, 8, 26));
    }

    @Test
    void fallsBackToTheLatestSameDirectionDealWhenNoneQualifyYet() {
        List<AnchorCandidateDeal> deals = List.of(
            deal(LocalDate.of(2026, 8, 25), "BUY", MaterialityLevel.LOW),
            deal(LocalDate.of(2026, 8, 26), "BUY", MaterialityLevel.LOW)
        );

        Optional<LocalDate> result = resolver.resolve(InstitutionalState.POSSIBLE_ACCUMULATION, null, null, deals);

        assertThat(result).contains(LocalDate.of(2026, 8, 26));
    }

    @Test
    void aMediumDealOnTheOppositeSideDoesNotAdvanceTheAnchor() {
        // The direct regression test: a same-materiality SELL arriving mid-accumulation is real,
        // but not evidence FOR the accumulation - must not restart the confirmation clock.
        LocalDate priorAnchor = LocalDate.of(2026, 8, 24);
        List<AnchorCandidateDeal> deals = List.of(
            deal(priorAnchor, "BUY", MaterialityLevel.HIGH),
            deal(LocalDate.of(2026, 8, 26), "SELL", MaterialityLevel.MEDIUM)
        );

        Optional<LocalDate> result = resolver.resolve(
            InstitutionalState.POSSIBLE_ACCUMULATION, InstitutionalState.POSSIBLE_ACCUMULATION, priorAnchor, deals
        );

        assertThat(result).contains(priorAnchor);
    }

    @Test
    void anImmaterialSameDirectionDealDoesNotAdvanceTheAnchor() {
        LocalDate priorAnchor = LocalDate.of(2026, 8, 24);
        List<AnchorCandidateDeal> deals = List.of(
            deal(priorAnchor, "BUY", MaterialityLevel.HIGH),
            deal(LocalDate.of(2026, 8, 26), "BUY", MaterialityLevel.LOW)
        );

        Optional<LocalDate> result = resolver.resolve(
            InstitutionalState.POSSIBLE_ACCUMULATION, InstitutionalState.POSSIBLE_ACCUMULATION, priorAnchor, deals
        );

        assertThat(result).contains(priorAnchor);
    }

    @Test
    void aMediumPlusSameDirectionDealAfterThePriorAnchorAdvancesIt() {
        LocalDate priorAnchor = LocalDate.of(2026, 8, 20);
        LocalDate newQualifying = LocalDate.of(2026, 8, 26);
        List<AnchorCandidateDeal> deals = List.of(
            deal(priorAnchor, "BUY", MaterialityLevel.HIGH),
            deal(newQualifying, "BUY", MaterialityLevel.MEDIUM)
        );

        Optional<LocalDate> result = resolver.resolve(
            InstitutionalState.POSSIBLE_ACCUMULATION, InstitutionalState.POSSIBLE_ACCUMULATION, priorAnchor, deals
        );

        assertThat(result).contains(newQualifying);
    }

    @Test
    void aGenuineStateFlipAlwaysResetsTheAnchorToTheNewSideRegardlessOfTheOldAnchor() {
        LocalDate oldAnchor = LocalDate.of(2026, 8, 20);
        LocalDate newSellDate = LocalDate.of(2026, 8, 26);
        List<AnchorCandidateDeal> deals = List.of(
            deal(oldAnchor, "BUY", MaterialityLevel.HIGH),
            deal(newSellDate, "SELL", MaterialityLevel.HIGH)
        );

        Optional<LocalDate> result = resolver.resolve(
            InstitutionalState.POSSIBLE_DISTRIBUTION, InstitutionalState.POSSIBLE_ACCUMULATION, oldAnchor, deals
        );

        assertThat(result).contains(newSellDate);
    }
}
