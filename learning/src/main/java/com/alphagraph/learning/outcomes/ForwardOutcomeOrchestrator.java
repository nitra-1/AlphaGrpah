package com.alphagraph.learning.outcomes;

import com.alphagraph.intelligence.priceadjustment.AdjustedDailyPrice;
import com.alphagraph.intelligence.priceadjustment.PriceAdjustmentService;
import com.alphagraph.learning.snapshot.DecisionSnapshot;
import com.alphagraph.learning.snapshot.DecisionSnapshotReader;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Fills in whichever forward-outcome horizons have newly become computable since the last run - incremental and idempotent, never recomputes a horizon already on record. */
@Component
public class ForwardOutcomeOrchestrator {

    private final DecisionSnapshotReader snapshotReader;
    private final ForwardOutcomeReader outcomeReader;
    private final ForwardOutcomeStore outcomeStore;
    private final PriceAdjustmentService priceAdjustmentService;
    private final ForwardOutcomeEngine engine;

    public ForwardOutcomeOrchestrator(
        DecisionSnapshotReader snapshotReader, ForwardOutcomeReader outcomeReader, ForwardOutcomeStore outcomeStore,
        PriceAdjustmentService priceAdjustmentService, ForwardOutcomeEngine engine
    ) {
        this.snapshotReader = snapshotReader;
        this.outcomeReader = outcomeReader;
        this.outcomeStore = outcomeStore;
        this.priceAdjustmentService = priceAdjustmentService;
        this.engine = engine;
    }

    public void computeAllPending() {
        List<DecisionSnapshot> snapshots = snapshotReader.findAllBefore(LocalDate.now());
        for (DecisionSnapshot snapshot : snapshots) {
            Set<Integer> alreadyComputed = outcomeReader.findComputedHorizons(snapshot.instrumentId(), snapshot.asOfDate());
            if (alreadyComputed.size() == 4) {
                continue;
            }
            List<AdjustedDailyPrice> priceHistory = priceAdjustmentService.adjustedHistory(snapshot.instrumentId());
            List<ForwardOutcome> newOutcomes = engine.computeOutcomes(snapshot, priceHistory, alreadyComputed);
            for (ForwardOutcome outcome : newOutcomes) {
                outcomeStore.save(outcome);
            }
        }
    }
}
