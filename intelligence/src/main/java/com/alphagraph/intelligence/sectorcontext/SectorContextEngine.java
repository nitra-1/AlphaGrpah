package com.alphagraph.intelligence.sectorcontext;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure calculation - no JDBC, no I/O - same convention as
 * {@code ownership.transformation.OwnershipTransformationEngine}. Takes an already-aligned,
 * already-resolved ascending series (the orchestrator does all three metrics' different reads and
 * date-matching beforehand) and just computes change/persistence over it - deliberately the same
 * shape for all three metrics, whether the series came from a computed spread
 * (VS_NIFTY/VS_SECTOR) or a straight re-persisted value (SECTOR_RELATIVE_STRENGTH).
 */
@Component
class SectorContextEngine {

    private static final int PERSISTENCE_WINDOW_DAYS = 20;
    private static final double CONFIDENCE = 90.0;
    private static final double FIRST_OBSERVATION_CONFIDENCE = 40.0;

    Optional<SectorContextEvidenceObservation> calculate(SectorContextMetric metric, UUID instrumentId, String symbol, List<DatedValue> ascending) {
        if (ascending.isEmpty()) {
            return Optional.empty();
        }
        DatedValue current = ascending.get(ascending.size() - 1);

        if (ascending.size() < 2) {
            return Optional.of(new SectorContextEvidenceObservation(
                metric, instrumentId, symbol, current.date(), null, current.value(), null, null, 0, FIRST_OBSERVATION_CONFIDENCE
            ));
        }

        DatedValue prior = ascending.get(ascending.size() - 2);
        BigDecimal change = current.value().subtract(prior.value());
        int persistence = persistenceDays(ascending, change.signum());

        return Optional.of(new SectorContextEvidenceObservation(
            metric, instrumentId, symbol, current.date(), prior.date(), current.value(), prior.value(), change, persistence, CONFIDENCE
        ));
    }

    /** Counts consecutive same-sign day-over-day transitions, walking backward from the most recent one, bounded so a permanent streak can't loop forever. */
    private static int persistenceDays(List<DatedValue> ascending, int currentSign) {
        if (currentSign == 0) {
            return 0;
        }
        int windowStart = Math.max(0, ascending.size() - 1 - PERSISTENCE_WINDOW_DAYS);
        int count = 0;
        for (int i = ascending.size() - 1; i > windowStart; i--) {
            int sign = ascending.get(i).value().subtract(ascending.get(i - 1).value()).signum();
            if (sign != currentSign) {
                break;
            }
            count++;
        }
        return count;
    }
}
