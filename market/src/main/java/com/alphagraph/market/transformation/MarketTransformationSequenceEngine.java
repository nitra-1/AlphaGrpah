package com.alphagraph.market.transformation;

import com.alphagraph.common.rules.RuleSet;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Pure calculation - no JDBC, no I/O - same convention as every Stage 2 engine. Detects 3
 * transformation sequences (docs/008_Stage3_Sequence_Detection_Specification.md §14.3) by walking
 * a real, ascending Market Stage 2 history.
 *
 * <p><b>Step detection uses reason codes, never {@code primary_state}</b> - Market's own priority
 * ladder ({@code EARLY_PRICE_PARTICIPATION} &gt; {@code STEALTH_ACCUMULATION_CANDIDATE} &gt;
 * {@code SUSTAINED_DELIVERY_ACCUMULATION} &gt; {@code DELIVERY_EXPANSION}/{@code RELATIVE_VOLUME_EXPANSION})
 * means a higher state winning on a given day can mask a lower sub-condition that's still
 * genuinely true underneath it. Every reason code {@code MarketInflectionEngine} writes is
 * unconditional on the underlying boolean, regardless of which state wins that day - so this
 * engine checks those directly.
 *
 * <p><b>Step confidence/persistence come from the reason's own underlying Stage 1 metric, never
 * the day's winning Stage 2 state's driving-metric numbers</b> - the day a composite state wins,
 * {@code market.inflection_states}' own stored confidence/persistence reflect whichever single
 * metric drove <i>that</i> state, not necessarily the metric behind the specific reason code this
 * engine is consuming. Each reason code is mapped to its real underlying metric's own
 * {@link MarketEvidenceObservation} instead (see {@link #evidenceFor}).
 *
 * <p><b>Trading-session gaps are counted by real row index</b>, never calendar days -
 * {@code market.inflection_states} only ever has rows for real trading days, so "N sessions apart"
 * is exactly "N real rows apart" in the ascending history list.
 *
 * <p><b>Attempt lifecycle</b>: a brand-new {@link Attempt} (never a mutated old one) starts every
 * time a sequence's first step fires while no attempt is active or the active one has reached a
 * terminal phase ({@code COMPLETE}/{@code BROKEN}) - this is what prevents old evidence leaking
 * into a later cycle. A sequence produces {@link Optional#empty()} when its first step has never
 * fired anywhere in the lookback window - genuine silence never manufactures a row (readiness is
 * tracked independently, see {@link #evaluateReadiness}).
 */
@Component
class MarketTransformationSequenceEngine {

    private static final int MIN_HISTORY_SESSIONS_FOR_READY = 5;
    private static final int PERSISTENCE_CAP = 10;
    private static final double THINNESS_PENALTY = 10.0;
    private static final double STALENESS_PENALTY = 10.0;
    private static final int RULE_VERSION = 1;

    private static final String DELIVERY_RISING = "DELIVERY_RISING";
    private static final String RELATIVE_VOLUME_RISING = "RELATIVE_VOLUME_RISING";
    private static final String DELIVERY_SUSTAINED = "DELIVERY_EXPANSION_SUSTAINED_5D";
    private static final String STEALTH_CANDIDATE = "VOLUME_DELIVERY_UP_PRICE_FLAT";
    private static final String BREAKOUT = "BREAKOUT_FROM_STEALTH_ACCUMULATION";

    MarketSequenceReadinessResult evaluateReadiness(UUID instrumentId, String symbol, LocalDate asOfDate, List<MarketInflectionHistoryEntry> historyAscending) {
        int sessions = historyAscending.size();
        MarketSequenceReadiness readiness = sessions >= MIN_HISTORY_SESSIONS_FOR_READY ? MarketSequenceReadiness.READY : MarketSequenceReadiness.INSUFFICIENT_HISTORY;
        return new MarketSequenceReadinessResult(instrumentId, symbol, asOfDate, sessions, readiness);
    }

    Optional<MarketSequenceResult> evaluateDeliveryLedAccumulation(
        UUID instrumentId, String symbol, List<MarketInflectionHistoryEntry> historyAscending, RuleSet rules
    ) {
        int maxGap = MarketSequenceRuleSetLoader.ruleThreshold(rules, "stage3-delivery-led-accumulation-max-gap", 3);
        int maxAge = MarketSequenceRuleSetLoader.ruleThreshold(rules, "stage3-sequence-max-age", 40);
        List<String> steps = List.of(DELIVERY_RISING, DELIVERY_SUSTAINED);
        Attempt attempt = walkSimpleSequence(historyAscending, steps, maxGap, maxAge);
        return buildResult(instrumentId, symbol, MarketSequenceType.DELIVERY_LED_ACCUMULATION, historyAscending, attempt, steps.size(), maxGap);
    }

    Optional<MarketSequenceResult> evaluateStealthAccumulationSequence(
        UUID instrumentId, String symbol, List<MarketInflectionHistoryEntry> historyAscending, RuleSet rules
    ) {
        int maxWindow = MarketSequenceRuleSetLoader.ruleThreshold(rules, "stage3-stealth-formation-max-window", 20);
        int maxAge = MarketSequenceRuleSetLoader.ruleThreshold(rules, "stage3-sequence-max-age", 40);
        Attempt attempt = walkStealthSequence(historyAscending, maxWindow, maxAge);
        return buildResult(instrumentId, symbol, MarketSequenceType.STEALTH_ACCUMULATION_SEQUENCE, historyAscending, attempt, 2, maxWindow);
    }

    Optional<MarketSequenceResult> evaluateMarketRecognitionSequence(
        UUID instrumentId, String symbol, List<MarketInflectionHistoryEntry> historyAscending, RuleSet rules
    ) {
        int maxGap = MarketSequenceRuleSetLoader.ruleThreshold(rules, "stage3-market-recognition-max-gap", 10);
        int maxAge = MarketSequenceRuleSetLoader.ruleThreshold(rules, "stage3-sequence-max-age", 40);
        List<String> steps = List.of(DELIVERY_SUSTAINED, STEALTH_CANDIDATE, BREAKOUT);
        Attempt attempt = walkSimpleSequence(historyAscending, steps, maxGap, maxAge);
        return buildResult(instrumentId, symbol, MarketSequenceType.MARKET_RECOGNITION_SEQUENCE, historyAscending, attempt, steps.size(), maxGap);
    }

    // ---- generic attempt-lifecycle walk for sequences whose steps are each a single reason-code check ----

    private Attempt walkSimpleSequence(List<MarketInflectionHistoryEntry> history, List<String> stepReasonCodes, int maxGapSessions, int maxAgeSessions) {
        int totalSteps = stepReasonCodes.size();
        Attempt attempt = null;
        for (int i = 0; i < history.size(); i++) {
            MarketInflectionHistoryEntry entry = history.get(i);
            if (attempt == null || attempt.phase() == MarketSequencePhase.COMPLETE || attempt.phase() == MarketSequencePhase.BROKEN) {
                if (entry.hasReason(stepReasonCodes.get(0))) {
                    attempt = Attempt.start(i, evidenceFor(stepReasonCodes.get(0), entry));
                }
            } else {
                String nextReason = stepReasonCodes.get(attempt.currentStep());
                int gap = i - attempt.lastStepIndex();
                if (entry.hasReason(nextReason)) {
                    attempt = attempt.advance(i, totalSteps, evidenceFor(nextReason, entry));
                } else if (gap > maxGapSessions || (i - attempt.firstIndex()) > maxAgeSessions) {
                    attempt = attempt.close();
                }
            }
        }
        return attempt;
    }

    // ---- Stealth-specific walk: step 1 is a bounded pairing of two independent prerequisites ----

    private Attempt walkStealthSequence(List<MarketInflectionHistoryEntry> history, int maxWindowSessions, int maxAgeSessions) {
        PrereqPairing[] pairings = stealthPrerequisitePairings(history, maxWindowSessions);
        Attempt attempt = null;
        for (int i = 0; i < history.size(); i++) {
            MarketInflectionHistoryEntry entry = history.get(i);
            if (attempt == null || attempt.phase() == MarketSequencePhase.COMPLETE || attempt.phase() == MarketSequencePhase.BROKEN) {
                if (pairings[i] != null) {
                    StepEvidence pairEvidence = jointEvidence(
                        evidenceFor(DELIVERY_RISING, history.get(pairings[i].deliveryIndex())),
                        evidenceFor(RELATIVE_VOLUME_RISING, history.get(pairings[i].volumeIndex()))
                    );
                    attempt = Attempt.start(i, pairEvidence);
                }
            } else {
                int gap = i - attempt.lastStepIndex();
                if (entry.hasReason(STEALTH_CANDIDATE)) {
                    attempt = attempt.advance(i, 2, evidenceFor(STEALTH_CANDIDATE, entry));
                } else if (gap > maxWindowSessions || (i - attempt.firstIndex()) > maxAgeSessions) {
                    attempt = attempt.close();
                }
            }
        }
        return attempt;
    }

    /**
     * Each prerequisite's own window is anchored to its <i>first</i> occurrence since the last
     * reset (never refreshed by a later recurrence of the same kind while still waiting) - a real
     * pairing only counts when both prerequisites are independently true within
     * {@code maxWindowSessions} sessions of each other. Same-session (both true on day i) and
     * either order are both valid.
     */
    private static PrereqPairing[] stealthPrerequisitePairings(List<MarketInflectionHistoryEntry> history, int maxWindowSessions) {
        PrereqPairing[] pairings = new PrereqPairing[history.size()];
        Integer pendingDeliveryIndex = null;
        Integer pendingVolumeIndex = null;
        for (int i = 0; i < history.size(); i++) {
            MarketInflectionHistoryEntry entry = history.get(i);
            if (pendingDeliveryIndex != null && (i - pendingDeliveryIndex) > maxWindowSessions) {
                pendingDeliveryIndex = null;
            }
            if (pendingVolumeIndex != null && (i - pendingVolumeIndex) > maxWindowSessions) {
                pendingVolumeIndex = null;
            }
            if (entry.hasReason(DELIVERY_RISING) && pendingDeliveryIndex == null) {
                pendingDeliveryIndex = i;
            }
            if (entry.hasReason(RELATIVE_VOLUME_RISING) && pendingVolumeIndex == null) {
                pendingVolumeIndex = i;
            }
            if (pendingDeliveryIndex != null && pendingVolumeIndex != null) {
                pairings[i] = new PrereqPairing(pendingDeliveryIndex, pendingVolumeIndex);
                pendingDeliveryIndex = null;
                pendingVolumeIndex = null;
            }
        }
        return pairings;
    }

    private record PrereqPairing(int deliveryIndex, int volumeIndex) {
    }

    // ---- result assembly: confidence/strength from the Attempt's own captured step evidence ----

    private Optional<MarketSequenceResult> buildResult(
        UUID instrumentId, String symbol, MarketSequenceType sequenceType,
        List<MarketInflectionHistoryEntry> history, Attempt attempt, int totalSteps, int maxGapSessions
    ) {
        if (attempt == null) {
            return Optional.empty();
        }

        LocalDate asOfDate = history.get(history.size() - 1).asOfDate();
        LocalDate firstStepDate = history.get(attempt.firstIndex()).asOfDate();
        LocalDate lastStepDate = history.get(attempt.lastStepIndex()).asOfDate();

        double confidence = weightedConfidence(attempt, totalSteps);
        boolean approachingExpiry = attempt.phase() != MarketSequencePhase.COMPLETE
            && (history.size() - 1 - attempt.lastStepIndex()) > maxGapSessions / 2.0;
        if (approachingExpiry) {
            confidence -= STALENESS_PENALTY;
        }
        confidence = clamp(confidence, 0.0, 100.0);

        double completionPct = (double) attempt.currentStep() / totalSteps * 100.0;
        double persistencePct = Math.min(100.0, attempt.stepPersistenceDays().get(attempt.stepPersistenceDays().size() - 1) / (double) PERSISTENCE_CAP * 100.0);
        double strength = clamp(0.80 * completionPct + 0.20 * persistencePct, 0.0, 100.0);

        List<ReasonCode> reasons = new ArrayList<>();
        if (attempt.currentStep() >= 1) {
            reasons.add(ReasonCode.of("FIRST_STEP_DETECTED"));
        }
        if (attempt.currentStep() >= 2) {
            reasons.add(ReasonCode.of("SECOND_STEP_DETECTED"));
        }
        if (attempt.phase() == MarketSequencePhase.COMPLETE) {
            reasons.add(ReasonCode.of("ALL_REQUIRED_STEPS_COMPLETE"));
        }
        if (attempt.phase() == MarketSequencePhase.BROKEN) {
            reasons.add(ReasonCode.of("SEQUENCE_EXPIRED"));
        }

        return Optional.of(new MarketSequenceResult(
            instrumentId, symbol, asOfDate, sequenceType, attempt.phase(),
            attempt.currentStep(), totalSteps, firstStepDate, lastStepDate,
            strength, confidence, RULE_VERSION, reasons
        ));
    }

    private static double weightedConfidence(Attempt attempt, int totalSteps) {
        double[] fullWeights = totalSteps == 2 ? new double[] {0.4, 0.6} : new double[] {0.2, 0.3, 0.5};
        int completed = attempt.stepConfidences().size();
        double weightSum = 0;
        for (int i = 0; i < completed; i++) {
            weightSum += fullWeights[i];
        }
        double weighted = 0;
        for (int i = 0; i < completed; i++) {
            weighted += attempt.stepConfidences().get(i) * (fullWeights[i] / weightSum);
        }
        boolean lastStepHasPrior = attempt.stepHasPrior().get(completed - 1);
        return lastStepHasPrior ? weighted : weighted - THINNESS_PENALTY;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // ---- reason code -> underlying Stage 1 metric evidence mapping (Correction 3) ----

    private record StepEvidence(double confidence, int persistenceDays, boolean hasPrior) {
    }

    private static StepEvidence evidenceFor(String reasonCode, MarketInflectionHistoryEntry entry) {
        return switch (reasonCode) {
            case DELIVERY_RISING, DELIVERY_SUSTAINED -> fromObservation(entry.delivery());
            case RELATIVE_VOLUME_RISING -> fromObservation(entry.relativeVolume());
            case BREAKOUT -> fromObservation(entry.priceReturn());
            case STEALTH_CANDIDATE -> jointStealthEvidence(entry);
            default -> throw new IllegalArgumentException("Unknown reason code: " + reasonCode);
        };
    }

    private static StepEvidence fromObservation(MarketEvidenceObservation observation) {
        if (observation == null) {
            // Defensive only - a reason code firing structurally implies its underlying evidence
            // exists (MarketInflectionEngine derives both from the same read); this should never
            // actually happen against real data.
            return new StepEvidence(0.0, 0, false);
        }
        return new StepEvidence(observation.confidence(), observation.persistenceDays(), observation.priorTradeDate() != null);
    }

    /** VOLUME_DELIVERY_UP_PRICE_FLAT depends on delivery + relative-volume + price jointly - confidence is the minimum across whichever of the 3 are present that day (only as trustworthy as the weakest input). */
    private static StepEvidence jointStealthEvidence(MarketInflectionHistoryEntry entry) {
        List<MarketEvidenceObservation> present = Stream.of(entry.delivery(), entry.relativeVolume(), entry.priceReturn())
            .filter(o -> o != null).toList();
        if (present.isEmpty()) {
            return new StepEvidence(0.0, 0, false);
        }
        double minConfidence = present.stream().mapToDouble(MarketEvidenceObservation::confidence).min().orElseThrow();
        int minPersistence = present.stream().mapToInt(MarketEvidenceObservation::persistenceDays).min().orElseThrow();
        boolean allHavePrior = present.stream().allMatch(o -> o.priorTradeDate() != null);
        return new StepEvidence(minConfidence, minPersistence, allHavePrior);
    }

    private static StepEvidence jointEvidence(StepEvidence a, StepEvidence b) {
        return new StepEvidence(Math.min(a.confidence(), b.confidence()), Math.min(a.persistenceDays(), b.persistenceDays()), a.hasPrior() && b.hasPrior());
    }

    // ---- immutable attempt state ----

    private record Attempt(
        int currentStep, int firstIndex, int lastStepIndex, MarketSequencePhase phase,
        List<Double> stepConfidences, List<Integer> stepPersistenceDays, List<Boolean> stepHasPrior
    ) {
        static Attempt start(int index, StepEvidence evidence) {
            return new Attempt(1, index, index, MarketSequencePhase.FORMING,
                List.of(evidence.confidence()), List.of(evidence.persistenceDays()), List.of(evidence.hasPrior()));
        }

        Attempt advance(int index, int totalSteps, StepEvidence evidence) {
            int newStep = currentStep + 1;
            List<Double> newConfidences = append(stepConfidences, evidence.confidence());
            List<Integer> newPersistence = append(stepPersistenceDays, evidence.persistenceDays());
            List<Boolean> newHasPrior = append(stepHasPrior, evidence.hasPrior());
            MarketSequencePhase newPhase = newStep == totalSteps ? MarketSequencePhase.COMPLETE : MarketSequencePhase.PROGRESSING;
            return new Attempt(newStep, firstIndex, index, newPhase, newConfidences, newPersistence, newHasPrior);
        }

        Attempt close() {
            return new Attempt(currentStep, firstIndex, lastStepIndex, MarketSequencePhase.BROKEN, stepConfidences, stepPersistenceDays, stepHasPrior);
        }

        private static <T> List<T> append(List<T> list, T value) {
            List<T> copy = new ArrayList<>(list);
            copy.add(value);
            return List.copyOf(copy);
        }
    }
}
