package com.alphagraph.learning.outcomes;

import com.alphagraph.intelligence.priceadjustment.AdjustedDailyPrice;
import com.alphagraph.intelligence.priceadjustment.PriceAdjustmentService;
import com.alphagraph.learning.snapshot.DecisionSnapshot;
import com.alphagraph.learning.snapshot.DecisionSnapshotReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Fills in whichever forward-outcome horizons have newly become computable since the last run -
 * incremental and idempotent, never recomputes a {@code CURRENT} horizon already on record.
 *
 * <p>Outcome Evidence Enrichment: also runs {@link ForwardOutcomeInvalidator} first each pass, then
 * recomputes whatever it just flagged {@code INVALIDATED} - both inside this same cron, no new
 * scheduler. A snapshot is immutable so recomputing re-fetches it fresh via
 * {@link DecisionSnapshotReader#findOne} rather than trusting anything already in
 * {@code forward_outcomes} (which is exactly the derived, correctable data being fixed).
 */
@Component
public class ForwardOutcomeOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ForwardOutcomeOrchestrator.class);

    private final DecisionSnapshotReader snapshotReader;
    private final ForwardOutcomeReader outcomeReader;
    private final ForwardOutcomeStore outcomeStore;
    private final PriceAdjustmentService priceAdjustmentService;
    private final ForwardOutcomeEngine engine;
    private final ForwardOutcomeInvalidator invalidator;

    public ForwardOutcomeOrchestrator(
        DecisionSnapshotReader snapshotReader, ForwardOutcomeReader outcomeReader, ForwardOutcomeStore outcomeStore,
        PriceAdjustmentService priceAdjustmentService, ForwardOutcomeEngine engine, ForwardOutcomeInvalidator invalidator
    ) {
        this.snapshotReader = snapshotReader;
        this.outcomeReader = outcomeReader;
        this.outcomeStore = outcomeStore;
        this.priceAdjustmentService = priceAdjustmentService;
        this.engine = engine;
        this.invalidator = invalidator;
    }

    public void computeAllPending() {
        invalidator.invalidateAffectedOutcomes();
        recomputeInvalidated();

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

    private void recomputeInvalidated() {
        List<ForwardOutcomeReader.InvalidatedOutcome> invalidated = outcomeReader.findInvalidated();
        for (ForwardOutcomeReader.InvalidatedOutcome target : invalidated) {
            Optional<DecisionSnapshot> snapshot = snapshotReader.findOne(target.instrumentId(), target.asOfDate());
            if (snapshot.isEmpty()) {
                log.error(
                    "Cannot recompute invalidated forward outcome for instrument {} on {} - the original decision snapshot no longer exists",
                    target.instrumentId(), target.asOfDate()
                );
                continue;
            }
            List<AdjustedDailyPrice> priceHistory = priceAdjustmentService.adjustedHistory(target.instrumentId());
            ForwardOutcome recomputed = engine.recomputeSingleOutcome(snapshot.get(), priceHistory, target.horizonDays());
            if (recomputed != null) {
                outcomeStore.recompute(recomputed);
            }
        }
    }
}
