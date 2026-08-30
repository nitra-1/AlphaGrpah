package com.alphagraph.ownership.interpretation;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Decides {@code event_anchor_date} - advances only for a same-direction, materially-relevant
 * deal, never any new deal. A candidate deal can advance the anchor only if both (a)
 * {@code materiality_level >= MEDIUM} and (b) its {@code buy_sell} side matches the *current*
 * institutional state's confirming direction (BUY for {@code POSSIBLE_ACCUMULATION}, SELL for
 * {@code POSSIBLE_DISTRIBUTION}). A MEDIUM+ deal on the confirming side is treated as a material
 * contributor by construction (v1 simplification) - materiality scoring already accounts for a
 * deal's own significance (ADTV ratio, repetition, breadth), so no separate third check is
 * layered on top. If {@code institutionalState} genuinely differs from the prior interpretation's
 * state, that is unambiguously a new event: the anchor resets using only deals on the *new*
 * state's confirming side, regardless of the old anchor.
 */
@Component
class ConfirmationAnchorResolver {

    Optional<LocalDate> resolve(
        InstitutionalState institutionalState, InstitutionalState priorState, LocalDate priorAnchorDate,
        List<AnchorCandidateDeal> windowDeals
    ) {
        String direction = confirmingSide(institutionalState);
        if (direction == null) {
            return Optional.empty();
        }

        boolean isNewEvent = priorState != institutionalState || priorAnchorDate == null;

        List<AnchorCandidateDeal> qualifying = windowDeals.stream()
            .filter(d -> direction.equals(d.buySell()))
            .filter(d -> d.materialityLevel().atLeast(MaterialityLevel.MEDIUM))
            .filter(d -> isNewEvent || d.dealDate().isAfter(priorAnchorDate))
            .toList();

        if (!qualifying.isEmpty()) {
            return qualifying.stream().map(AnchorCandidateDeal::dealDate).max(LocalDate::compareTo);
        }

        if (isNewEvent) {
            // No MEDIUM+ same-direction deal yet - fall back to the single latest same-direction
            // deal of any materiality. Self-correcting: low materiality means a low confirmation
            // score anyway, never fabricated as a strong signal.
            return windowDeals.stream()
                .filter(d -> direction.equals(d.buySell()))
                .map(AnchorCandidateDeal::dealDate)
                .max(LocalDate::compareTo);
        }

        // Not a new event and nothing qualifies past the current anchor - hold still.
        return Optional.of(priorAnchorDate);
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
}
