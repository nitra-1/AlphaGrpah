package com.alphagraph.learning.outcomes;

import com.alphagraph.decision.api.DecisionRating;
import com.alphagraph.intelligence.priceadjustment.AdjustedDailyPrice;
import com.alphagraph.intelligence.priceadjustment.PriceAdjustmentService;
import com.alphagraph.learning.snapshot.DecisionSnapshot;
import com.alphagraph.learning.snapshot.DecisionSnapshotReader;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ForwardOutcomeOrchestratorTest {

    private final DecisionSnapshotReader snapshotReader = mock(DecisionSnapshotReader.class);
    private final ForwardOutcomeReader outcomeReader = mock(ForwardOutcomeReader.class);
    private final ForwardOutcomeStore outcomeStore = mock(ForwardOutcomeStore.class);
    private final PriceAdjustmentService priceAdjustmentService = mock(PriceAdjustmentService.class);
    private final ForwardOutcomeEngine engine = mock(ForwardOutcomeEngine.class);
    private final ForwardOutcomeOrchestrator orchestrator =
        new ForwardOutcomeOrchestrator(snapshotReader, outcomeReader, outcomeStore, priceAdjustmentService, engine);

    private final UUID instrumentId = UUID.randomUUID();
    private final LocalDate asOfDate = LocalDate.of(2026, 8, 1);

    @Test
    void skipsPriceLookupWhenAllFourHorizonsAlreadyComputed() {
        DecisionSnapshot snapshot = snapshot();
        when(snapshotReader.findAllBefore(any())).thenReturn(List.of(snapshot));
        when(outcomeReader.findComputedHorizons(instrumentId, asOfDate)).thenReturn(Set.of(5, 10, 20, 60));

        orchestrator.computeAllPending();

        verify(priceAdjustmentService, never()).adjustedHistory(any());
    }

    @Test
    void fetchesPricesAndSavesNewOutcomesWhenHorizonsAreMissing() {
        DecisionSnapshot snapshot = snapshot();
        List<AdjustedDailyPrice> prices = List.of();
        ForwardOutcome outcome = outcome();
        when(snapshotReader.findAllBefore(any())).thenReturn(List.of(snapshot));
        when(outcomeReader.findComputedHorizons(instrumentId, asOfDate)).thenReturn(Set.of());
        when(priceAdjustmentService.adjustedHistory(instrumentId)).thenReturn(prices);
        when(engine.computeOutcomes(eq(snapshot), eq(prices), eq(Set.of()))).thenReturn(List.of(outcome));

        orchestrator.computeAllPending();

        verify(outcomeStore).save(outcome);
    }

    private DecisionSnapshot snapshot() {
        return new DecisionSnapshot(
            instrumentId, "TEST", asOfDate,
            60.0, DecisionRating.BUY, 1, 60.0, DecisionRating.BUY, 1,
            null, null, null, null, null, null,
            80.0, 1, Instant.now(), Instant.now()
        );
    }

    private ForwardOutcome outcome() {
        return new ForwardOutcome(
            instrumentId, "TEST", asOfDate, 5, asOfDate.plusDays(5),
            new BigDecimal("100.00"), new BigDecimal("105.00"), new BigDecimal("5.00"),
            DecisionRating.BUY, true, DecisionRating.BUY, true,
            null, null, null, null, null, null
        );
    }
}
