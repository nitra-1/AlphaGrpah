package com.alphagraph.learning.outcomes;

import com.alphagraph.corporate.api.CorporateAction;
import com.alphagraph.intelligence.priceadjustment.PriceAdjustmentService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Detects when a BONUS/SPLIT ingested after a forward outcome was computed changes the adjusted
 * price basis that outcome was measured against, and flags the affected row {@code INVALIDATED}
 * so {@link ForwardOutcomeOrchestrator} recomputes it. Never queries {@code corporate.corporate_actions}
 * directly - all action data comes through {@link PriceAdjustmentService#findPriceAffectingActions},
 * the same sanctioned cross-domain seam {@link ForwardOutcomeEngine} already uses (per
 * docs/001_System_Architecture.md §4 Rule 4).
 *
 * <p>A row is affected when a BONUS/SPLIT's {@code ex_date} falls strictly after the decision date
 * and on or before the outcome date (i.e. it actually happened somewhere inside the measured
 * window) and its {@code created_at} is newer than the outcome's own recorded
 * {@code priceAdjustmentWatermark} - meaning it wasn't known about the moment this outcome was
 * computed.
 */
@Component
class ForwardOutcomeInvalidator {

    private final ForwardOutcomeReader outcomeReader;
    private final ForwardOutcomeStore outcomeStore;
    private final PriceAdjustmentService priceAdjustmentService;

    ForwardOutcomeInvalidator(ForwardOutcomeReader outcomeReader, ForwardOutcomeStore outcomeStore, PriceAdjustmentService priceAdjustmentService) {
        this.outcomeReader = outcomeReader;
        this.outcomeStore = outcomeStore;
        this.priceAdjustmentService = priceAdjustmentService;
    }

    void invalidateAffectedOutcomes() {
        List<ForwardOutcomeReader.CurrentOutcome> current = outcomeReader.findCurrent();
        Map<UUID, List<ForwardOutcomeReader.CurrentOutcome>> byInstrument = current.stream()
            .collect(Collectors.groupingBy(ForwardOutcomeReader.CurrentOutcome::instrumentId));

        for (Map.Entry<UUID, List<ForwardOutcomeReader.CurrentOutcome>> entry : byInstrument.entrySet()) {
            List<CorporateAction> actions = priceAdjustmentService.findPriceAffectingActions(entry.getKey());
            if (actions.isEmpty()) {
                continue;
            }
            for (ForwardOutcomeReader.CurrentOutcome outcome : entry.getValue()) {
                if (isAffected(outcome, actions)) {
                    outcomeStore.markInvalidated(outcome.instrumentId(), outcome.asOfDate(), outcome.horizonDays());
                }
            }
        }
    }

    private static boolean isAffected(ForwardOutcomeReader.CurrentOutcome outcome, List<CorporateAction> actions) {
        for (CorporateAction action : actions) {
            if (action.createdAt() == null) {
                continue;
            }
            boolean withinWindow = action.exDate().isAfter(outcome.asOfDate()) && !action.exDate().isAfter(outcome.outcomeDate());
            boolean newerThanWatermark = outcome.priceAdjustmentWatermark() == null || action.createdAt().isAfter(outcome.priceAdjustmentWatermark());
            if (withinWindow && newerThanWatermark) {
                return true;
            }
        }
        return false;
    }
}
