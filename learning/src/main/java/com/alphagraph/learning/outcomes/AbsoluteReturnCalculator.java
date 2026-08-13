package com.alphagraph.learning.outcomes;

import com.alphagraph.decision.api.DecisionRating;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Close-to-close forward return and its directional interpretation against a rating/domain score -
 * extracted unchanged from the original {@code ForwardOutcomeEngine} as part of splitting it into
 * per-concern collaborators (absolute return / benchmark-relative return / excursion), so none of
 * them grows into a monster as Phase 4 adds more outcome labels later.
 */
@Component
class AbsoluteReturnCalculator {

    private static final int RETURN_SCALE = 2;

    BigDecimal computeReturn(BigDecimal referencePrice, BigDecimal outcomePrice) {
        return outcomePrice.subtract(referencePrice)
            .divide(referencePrice, 6, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(RETURN_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * STRONG_BUY/BUY are bullish - correct if the forward return is positive. REDUCE/AVOID are
     * bearish - correct if the forward return is zero or negative. HOLD is deliberately excluded
     * (null): a neutral rating has no honest "correct direction" to score against.
     */
    Boolean directionallyCorrect(DecisionRating rating, BigDecimal forwardReturn) {
        boolean positive = forwardReturn.signum() > 0;
        return switch (rating) {
            case STRONG_BUY, BUY -> positive;
            case REDUCE, AVOID -> !positive;
            case HOLD -> null;
        };
    }

    /**
     * A domain score above 50 is treated as a bullish signal, below 50 as bearish - the standard
     * midpoint every domain engine already scores against (0-100, 50 neutral). A score of exactly
     * 50, or a missing score, is null (not applicable), never an arbitrary guess. This convention
     * hasn't been checked against real outcome data yet - revisit once enough forward_outcomes
     * rows exist to see whether 50 is actually the right split point for each domain.
     */
    Boolean signalCorrect(Double domainScore, BigDecimal forwardReturn) {
        if (domainScore == null || domainScore == 50.0) {
            return null;
        }
        boolean positive = forwardReturn.signum() > 0;
        return domainScore > 50.0 ? positive : !positive;
    }
}
