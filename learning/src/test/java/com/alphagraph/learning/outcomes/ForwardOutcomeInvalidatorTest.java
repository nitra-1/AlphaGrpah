package com.alphagraph.learning.outcomes;

import com.alphagraph.corporate.api.CorporateAction;
import com.alphagraph.intelligence.priceadjustment.PriceAdjustmentService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ForwardOutcomeInvalidatorTest {

    private final ForwardOutcomeReader outcomeReader = mock(ForwardOutcomeReader.class);
    private final ForwardOutcomeStore outcomeStore = mock(ForwardOutcomeStore.class);
    private final PriceAdjustmentService priceAdjustmentService = mock(PriceAdjustmentService.class);
    private final ForwardOutcomeInvalidator invalidator =
        new ForwardOutcomeInvalidator(outcomeReader, outcomeStore, priceAdjustmentService);

    private final UUID instrumentId = UUID.randomUUID();
    private final LocalDate asOfDate = LocalDate.of(2026, 8, 1);
    private final LocalDate outcomeDate = LocalDate.of(2026, 8, 10);

    @Test
    void invalidatesWhenABonusExDateFallsInsideTheMeasuredWindowAndWasIngestedAfterTheWatermark() {
        Instant watermark = Instant.parse("2026-07-01T00:00:00Z");
        ForwardOutcomeReader.CurrentOutcome outcome = new ForwardOutcomeReader.CurrentOutcome(instrumentId, asOfDate, 5, outcomeDate, watermark);
        when(outcomeReader.findCurrent()).thenReturn(List.of(outcome));
        CorporateAction lateBonus = bonus(asOfDate.plusDays(3), watermark.plus(1, ChronoUnit.DAYS));
        when(priceAdjustmentService.findPriceAffectingActions(instrumentId)).thenReturn(List.of(lateBonus));

        invalidator.invalidateAffectedOutcomes();

        verify(outcomeStore).markInvalidated(instrumentId, asOfDate, 5);
    }

    @Test
    void doesNotInvalidateWhenTheActionExDateIsOutsideTheMeasuredWindow() {
        Instant watermark = Instant.parse("2026-07-01T00:00:00Z");
        ForwardOutcomeReader.CurrentOutcome outcome = new ForwardOutcomeReader.CurrentOutcome(instrumentId, asOfDate, 5, outcomeDate, watermark);
        when(outcomeReader.findCurrent()).thenReturn(List.of(outcome));
        // ex_date is after the outcome date entirely - the horizon already closed before this action happened.
        CorporateAction actionAfterWindow = bonus(outcomeDate.plusDays(5), watermark.plus(1, ChronoUnit.DAYS));
        when(priceAdjustmentService.findPriceAffectingActions(instrumentId)).thenReturn(List.of(actionAfterWindow));

        invalidator.invalidateAffectedOutcomes();

        verify(outcomeStore, never()).markInvalidated(any(UUID.class), any(LocalDate.class), eq(5));
    }

    @Test
    void doesNotInvalidateWhenTheActionWasAlreadyKnownAtComputationTime() {
        Instant watermark = Instant.parse("2026-07-15T00:00:00Z");
        ForwardOutcomeReader.CurrentOutcome outcome = new ForwardOutcomeReader.CurrentOutcome(instrumentId, asOfDate, 5, outcomeDate, watermark);
        when(outcomeReader.findCurrent()).thenReturn(List.of(outcome));
        // Inside the window, but created_at is before the watermark - already accounted for.
        CorporateAction alreadyKnownAction = bonus(asOfDate.plusDays(3), watermark.minus(1, ChronoUnit.DAYS));
        when(priceAdjustmentService.findPriceAffectingActions(instrumentId)).thenReturn(List.of(alreadyKnownAction));

        invalidator.invalidateAffectedOutcomes();

        verify(outcomeStore, never()).markInvalidated(any(UUID.class), any(LocalDate.class), eq(5));
    }

    @Test
    void invalidatesWhenNoWatermarkWasRecordedAndAnActionNowExistsInsideTheWindow() {
        ForwardOutcomeReader.CurrentOutcome outcome = new ForwardOutcomeReader.CurrentOutcome(instrumentId, asOfDate, 5, outcomeDate, null);
        when(outcomeReader.findCurrent()).thenReturn(List.of(outcome));
        CorporateAction anyAction = bonus(asOfDate.plusDays(1), Instant.now());
        when(priceAdjustmentService.findPriceAffectingActions(instrumentId)).thenReturn(List.of(anyAction));

        invalidator.invalidateAffectedOutcomes();

        verify(outcomeStore).markInvalidated(instrumentId, asOfDate, 5);
    }

    @Test
    void doesNothingWhenThereAreNoCorporateActionsForTheInstrument() {
        ForwardOutcomeReader.CurrentOutcome outcome = new ForwardOutcomeReader.CurrentOutcome(instrumentId, asOfDate, 5, outcomeDate, null);
        when(outcomeReader.findCurrent()).thenReturn(List.of(outcome));
        when(priceAdjustmentService.findPriceAffectingActions(instrumentId)).thenReturn(List.of());

        invalidator.invalidateAffectedOutcomes();

        verify(outcomeStore, never()).markInvalidated(any(UUID.class), any(LocalDate.class), org.mockito.ArgumentMatchers.anyInt());
    }

    private CorporateAction bonus(LocalDate exDate, Instant createdAt) {
        return new CorporateAction(instrumentId, "TEST", "BONUS", exDate, null, null, null, 1, 1, null, createdAt);
    }
}
