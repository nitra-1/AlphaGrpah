package com.alphagraph.ownership.interpretation;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Stage A "Discovery Confirmation" - built entirely from what's available for an untracked
 * symbol ({@code market.discovered_prices}, Sprint 2/3's own deal and participant data). Does
 * *not* recreate {@code TechnicalEngine}/{@code InstitutionalEngine} for untracked symbols - a
 * later "Full Confirmation" stage using those, applicable only after promotion, is an explicitly
 * separate future sprint.
 *
 * <p>Bounded T+1/T+3/T+5-trading-session lifecycle: {@code sessionsElapsed >= 5} freezes the
 * result permanently for the given anchor (see {@link ConfirmationAnchorResolver} for how the
 * anchor itself only advances for real, same-direction evidence) - confirmation is never judged
 * forever against a moving target. {@code NOT_APPLICABLE} for every non-directional
 * {@link InstitutionalState} - there's nothing directional to confirm.
 */
@Component
class DiscoveryConfirmationEngine {

    private static final int FREEZE_AT_SESSIONS = 5;
    private static final double PRICE_WEIGHT = 0.35;
    private static final double DELIVERY_WEIGHT = 0.25;
    private static final double VOLUME_WEIGHT = 0.20;
    private static final double REPEAT_WEIGHT = 0.20;

    DiscoveryConfirmationResult evaluate(
        InstitutionalState institutionalState, LocalDate anchorDate, List<AnchorCandidateDeal> anchorDateDeals,
        List<DiscoveredPriceRow> postAnchorSessions, List<DiscoveredPriceRow> preAnchorBaselineSessions,
        List<ParticipantDealActivity> postAnchorActivity
    ) {
        String direction = confirmingSide(institutionalState);
        if (direction == null || anchorDate == null) {
            return DiscoveryConfirmationResult.notApplicable();
        }

        int sessionsElapsed = postAnchorSessions.size();
        boolean frozen = sessionsElapsed >= FREEZE_AT_SESSIONS;

        List<WeightedComponent> components = new ArrayList<>();
        BigDecimal priceScore = priceScore(direction, anchorDateDeals, postAnchorSessions).orElse(null);
        if (priceScore != null) {
            components.add(new WeightedComponent(PRICE_WEIGHT, priceScore));
        }
        BigDecimal deliveryScore = deliveryScore(postAnchorSessions, preAnchorBaselineSessions).orElse(null);
        if (deliveryScore != null) {
            components.add(new WeightedComponent(DELIVERY_WEIGHT, deliveryScore));
        }
        BigDecimal volumeScore = volumeScore(postAnchorSessions, preAnchorBaselineSessions).orElse(null);
        if (volumeScore != null) {
            components.add(new WeightedComponent(VOLUME_WEIGHT, volumeScore));
        }
        BigDecimal repeatScore = repeatActivityScore(direction, postAnchorActivity);
        components.add(new WeightedComponent(REPEAT_WEIGHT, repeatScore));

        double coveragePct = 100.0 * components.size() / 4.0;
        BigDecimal confirmationScore = weightedAverage(components);
        // At T+0 there's no post-anchor market evidence yet (only the always-present repeat-
        // activity component) - that's "awaiting evidence," not "evidence came in negative", so
        // it must read as PENDING rather than falling through to a score-based FAILED band.
        DiscoveryConfirmationState state = sessionsElapsed == 0 ? DiscoveryConfirmationState.PENDING : band(confirmationScore);

        return new DiscoveryConfirmationResult(
            state, frozen, anchorDate, sessionsElapsed, confirmationScore, priceScore, deliveryScore,
            volumeScore, repeatScore, scale(BigDecimal.valueOf(coveragePct))
        );
    }

    private static String confirmingSide(InstitutionalState state) {
        if (state == InstitutionalState.POSSIBLE_ACCUMULATION) {
            return "BUY";
        }
        if (state == InstitutionalState.POSSIBLE_DISTRIBUTION) {
            return "SELL";
        }
        return null;
    }

    /** Directionally-pure weighted event price (confirming side only, at the anchor date) vs. the latest available close, signed by direction. */
    private static Optional<BigDecimal> priceScore(String direction, List<AnchorCandidateDeal> anchorDateDeals, List<DiscoveredPriceRow> postAnchorSessions) {
        if (postAnchorSessions.isEmpty()) {
            return Optional.empty();
        }
        List<AnchorCandidateDeal> sameSide = anchorDateDeals.stream().filter(d -> direction.equals(d.buySell())).toList();
        BigDecimal totalQuantity = sameSide.stream().map(AnchorCandidateDeal::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalQuantity.signum() == 0) {
            return Optional.empty();
        }
        BigDecimal weightedSum = sameSide.stream()
            .map(d -> d.price().multiply(d.quantity()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal weightedEventPrice = weightedSum.divide(totalQuantity, 4, RoundingMode.HALF_UP);
        if (weightedEventPrice.signum() == 0) {
            return Optional.empty();
        }

        BigDecimal latestClose = postAnchorSessions.get(postAnchorSessions.size() - 1).close();
        BigDecimal rawReturnPct = latestClose.subtract(weightedEventPrice)
            .divide(weightedEventPrice, 6, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
        BigDecimal signedReturnPct = "SELL".equals(direction) ? rawReturnPct.negate() : rawReturnPct;

        double score = 50 + (signedReturnPct.doubleValue() / 5.0) * 10;
        return Optional.of(scale(BigDecimal.valueOf(clamp(score))));
    }

    /** Percentage-point change (not relative %) in average delivery from the pre-anchor baseline to the post-anchor window - higher delivery confirms genuine investor interest regardless of direction. */
    private static Optional<BigDecimal> deliveryScore(List<DiscoveredPriceRow> postAnchorSessions, List<DiscoveredPriceRow> preAnchorBaselineSessions) {
        OptionalDouble postAvg = average(postAnchorSessions, DiscoveredPriceRow::deliveryPercentage);
        OptionalDouble preAvg = average(preAnchorBaselineSessions, DiscoveredPriceRow::deliveryPercentage);
        if (postAvg.isEmpty() || preAvg.isEmpty()) {
            return Optional.empty();
        }
        double deliveryChangePp = postAvg.getAsDouble() - preAvg.getAsDouble();
        double score = 50 + (deliveryChangePp / 10.0) * 25;
        return Optional.of(scale(BigDecimal.valueOf(clamp(score))));
    }

    /** Relative volume of the post-anchor window vs. the pre-anchor baseline. */
    private static Optional<BigDecimal> volumeScore(List<DiscoveredPriceRow> postAnchorSessions, List<DiscoveredPriceRow> preAnchorBaselineSessions) {
        OptionalDouble postAvg = average(postAnchorSessions, row -> (double) row.volume());
        OptionalDouble preAvg = average(preAnchorBaselineSessions, row -> (double) row.volume());
        if (postAvg.isEmpty() || preAvg.isEmpty() || preAvg.getAsDouble() == 0.0) {
            return Optional.empty();
        }
        double relativeVolume = postAvg.getAsDouble() / preAvg.getAsDouble();
        double score;
        if (relativeVolume >= 1.5) {
            score = 100;
        } else if (relativeVolume >= 1.0) {
            score = 50 + (relativeVolume - 1.0) / 0.5 * 50;
        } else if (relativeVolume >= 0.5) {
            score = (relativeVolume - 0.5) / 0.5 * 50;
        } else {
            score = 0;
        }
        return Optional.of(scale(BigDecimal.valueOf(clamp(score))));
    }

    /** Strictly post-anchor distinct same-direction institutional participants - never the 20-day interpretation window, so pre-anchor repetition can never inflate this. Always available (0 is a meaningful, real count). */
    private static BigDecimal repeatActivityScore(String direction, List<ParticipantDealActivity> postAnchorActivity) {
        Set<java.util.UUID> distinctSameSideInstitutional = postAnchorActivity.stream()
            .filter(a -> direction.equals(a.buySell()))
            .filter(a -> isInstitutional(a.participantType()))
            .map(ParticipantDealActivity::participantId)
            .collect(Collectors.toSet());
        int count = distinctSameSideInstitutional.size();
        double score = switch (Math.min(count, 3)) {
            case 0 -> 20;
            case 1 -> 50;
            case 2 -> 75;
            default -> 100;
        };
        return scale(BigDecimal.valueOf(score));
    }

    private static boolean isInstitutional(ParticipantType type) {
        return type == ParticipantType.MUTUAL_FUND || type == ParticipantType.INSURANCE
            || type == ParticipantType.FPI_FII || type == ParticipantType.SOVEREIGN_PENSION_FUND
            || type == ParticipantType.AIF;
    }

    private static OptionalDouble average(List<DiscoveredPriceRow> rows, java.util.function.Function<DiscoveredPriceRow, ?> extractor) {
        List<Double> values = new ArrayList<>();
        for (DiscoveredPriceRow row : rows) {
            Object value = extractor.apply(row);
            if (value instanceof BigDecimal bd) {
                values.add(bd.doubleValue());
            } else if (value instanceof Double d) {
                values.add(d);
            }
        }
        return values.isEmpty() ? OptionalDouble.empty() : OptionalDouble.of(values.stream().mapToDouble(Double::doubleValue).average().orElse(0));
    }

    private static BigDecimal weightedAverage(List<WeightedComponent> components) {
        double totalWeight = components.stream().mapToDouble(WeightedComponent::weight).sum();
        if (totalWeight == 0) {
            return scale(BigDecimal.ZERO);
        }
        double weightedSum = components.stream().mapToDouble(c -> c.weight() * c.score().doubleValue()).sum();
        return scale(BigDecimal.valueOf(clamp(weightedSum / totalWeight)));
    }

    private static DiscoveryConfirmationState band(BigDecimal score) {
        double value = score.doubleValue();
        if (value >= 75) {
            return DiscoveryConfirmationState.CONFIRMED;
        }
        if (value >= 55) {
            return DiscoveryConfirmationState.PARTIALLY_CONFIRMED;
        }
        if (value >= 30) {
            return DiscoveryConfirmationState.PENDING;
        }
        return DiscoveryConfirmationState.FAILED;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private record WeightedComponent(double weight, BigDecimal score) {
    }
}
