package com.alphagraph.market.transformation;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pure calculation - no JDBC, no I/O - same convention as
 * {@code ownership.transformation.OwnershipTransformationEngine}. Unlike Stage 1's own engine,
 * this does no math on raw prices at all - {@code MarketAccumulationEngine} already computed
 * change/velocity/persistence per metric; this just interprets the single most recent row per
 * metric into a banded state.
 *
 * <p>{@code priorTradingState} is a plain string (the caller resolves "the latest state strictly
 * before {@code asOfDate}", see {@code MarketInflectionStateReader}) rather than a typed lookup -
 * this engine never touches the database itself, so it never knows or cares whether that string
 * came from yesterday, last Friday, or nowhere at all.
 */
@Component
class MarketInflectionEngine {

    private static final int PERSISTENCE_CAP = 10;
    private static final int SUSTAINED_DELIVERY_PERSISTENCE_THRESHOLD = 5;
    private static final BigDecimal PRICE_QUIET_ZONE = new BigDecimal("3.0");
    private static final double BASE_CONFIDENCE = 90.0;
    private static final double THINNESS_PENALTY = 10.0;

    MarketInflectionResult calculate(
        UUID instrumentId, String symbol, LocalDate asOfDate,
        MarketEvidenceObservation delivery, MarketEvidenceObservation relativeVolume, MarketEvidenceObservation priceReturn,
        String priorTradingState
    ) {
        boolean deliveryExpansion = changePositive(delivery);
        boolean relativeVolumeExpansion = changePositive(relativeVolume)
            && relativeVolume.value() != null && relativeVolume.value().compareTo(BigDecimal.ONE) > 0;
        boolean sustainedDeliveryAccumulation = deliveryExpansion && delivery.persistenceDays() >= SUSTAINED_DELIVERY_PERSISTENCE_THRESHOLD;
        boolean stealthAccumulationCandidate = deliveryExpansion && relativeVolumeExpansion
            && priceReturn != null && priceReturn.value() != null && priceReturn.value().abs().compareTo(PRICE_QUIET_ZONE) <= 0;
        // The price has now emerged OUT of the quiet zone STEALTH_ACCUMULATION_CANDIDATE required -
        // this is about crossing out of that zone, not the size of one day's move. The change>0
        // guard excludes a day where value is still >3% but actually retreating, not a fresh breakout.
        boolean earlyPriceParticipation = MarketInflectionState.STEALTH_ACCUMULATION_CANDIDATE.name().equals(priorTradingState)
            && priceReturn != null && priceReturn.value() != null && priceReturn.value().compareTo(PRICE_QUIET_ZONE) > 0
            && changePositive(priceReturn);

        List<ReasonCode> reasons = new ArrayList<>();
        if (relativeVolumeExpansion) {
            reasons.add(ReasonCode.of("RELATIVE_VOLUME_RISING", relativeVolume.change().doubleValue()));
            reasons.add(ReasonCode.of("ABOVE_AVERAGE_VOLUME", relativeVolume.value().doubleValue()));
        }
        if (deliveryExpansion) {
            reasons.add(ReasonCode.of("DELIVERY_RISING", delivery.change().doubleValue()));
        }
        if (sustainedDeliveryAccumulation) {
            reasons.add(ReasonCode.of("DELIVERY_EXPANSION_SUSTAINED_5D", delivery.persistenceDays()));
        }
        if (stealthAccumulationCandidate) {
            reasons.add(ReasonCode.of("VOLUME_DELIVERY_UP_PRICE_FLAT", priceReturn.value().doubleValue()));
        }
        if (earlyPriceParticipation) {
            reasons.add(ReasonCode.of("BREAKOUT_FROM_STEALTH_ACCUMULATION", priceReturn.value().doubleValue()));
        }

        MarketInflectionState primaryState;
        if (earlyPriceParticipation) {
            primaryState = MarketInflectionState.EARLY_PRICE_PARTICIPATION;
        } else if (stealthAccumulationCandidate) {
            primaryState = MarketInflectionState.STEALTH_ACCUMULATION_CANDIDATE;
        } else if (sustainedDeliveryAccumulation) {
            primaryState = MarketInflectionState.SUSTAINED_DELIVERY_ACCUMULATION;
        } else if (deliveryExpansion) {
            primaryState = MarketInflectionState.DELIVERY_EXPANSION;
        } else if (relativeVolumeExpansion) {
            primaryState = MarketInflectionState.RELATIVE_VOLUME_EXPANSION;
        } else {
            primaryState = MarketInflectionState.NO_CLEAR_SIGNAL;
        }

        MarketEvidenceObservation driving = drivingObservationFor(primaryState, delivery, relativeVolume, priceReturn);
        int persistence = persistenceFor(primaryState, delivery, relativeVolume);
        double confidence = driving == null
            ? averageConfidence(delivery, relativeVolume, priceReturn)
            : clamp(BASE_CONFIDENCE + Math.min(10.0, 2.0 * persistence) - (driving.priorTradeDate() == null ? THINNESS_PENALTY : 0.0), 0.0, 100.0);

        return new MarketInflectionResult(
            instrumentId, symbol, asOfDate, primaryState, confidence,
            driving == null ? null : driving.metric(),
            driving == null ? null : driving.value(),
            driving == null ? null : driving.change(),
            (driving == null || driving.velocityPerDay() == null) ? null : MarketVelocityBanding.bandFor(driving.metric(), driving.velocityPerDay()),
            persistence, reasons
        );
    }

    private static boolean changePositive(MarketEvidenceObservation observation) {
        return observation != null && observation.change() != null && observation.change().signum() > 0;
    }

    private static MarketEvidenceObservation drivingObservationFor(
        MarketInflectionState primaryState, MarketEvidenceObservation delivery, MarketEvidenceObservation relativeVolume, MarketEvidenceObservation priceReturn
    ) {
        return switch (primaryState) {
            case DELIVERY_EXPANSION, SUSTAINED_DELIVERY_ACCUMULATION, STEALTH_ACCUMULATION_CANDIDATE -> delivery;
            case RELATIVE_VOLUME_EXPANSION -> relativeVolume;
            case EARLY_PRICE_PARTICIPATION -> priceReturn;
            case NO_CLEAR_SIGNAL -> null;
        };
    }

    /** STEALTH_ACCUMULATION_CANDIDATE is only as persistent as its weaker required component. EARLY_PRICE_PARTICIPATION is a crossing event, not a sustained one. */
    private static int persistenceFor(MarketInflectionState primaryState, MarketEvidenceObservation delivery, MarketEvidenceObservation relativeVolume) {
        return switch (primaryState) {
            case DELIVERY_EXPANSION, SUSTAINED_DELIVERY_ACCUMULATION -> Math.min(PERSISTENCE_CAP, delivery.persistenceDays());
            case RELATIVE_VOLUME_EXPANSION -> Math.min(PERSISTENCE_CAP, relativeVolume.persistenceDays());
            case STEALTH_ACCUMULATION_CANDIDATE ->
                Math.min(Math.min(PERSISTENCE_CAP, delivery.persistenceDays()), Math.min(PERSISTENCE_CAP, relativeVolume.persistenceDays()));
            case EARLY_PRICE_PARTICIPATION, NO_CLEAR_SIGNAL -> 0;
        };
    }

    private static double averageConfidence(MarketEvidenceObservation... observations) {
        double sum = 0;
        int count = 0;
        for (MarketEvidenceObservation observation : observations) {
            if (observation != null) {
                sum += observation.confidence();
                count++;
            }
        }
        return count == 0 ? 0.0 : Math.round((sum / count) * 100.0) / 100.0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
