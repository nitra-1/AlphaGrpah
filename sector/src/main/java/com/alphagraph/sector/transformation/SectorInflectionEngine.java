package com.alphagraph.sector.transformation;

import com.alphagraph.common.inflection.VelocityBand;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pure calculation - no JDBC, no I/O - same convention as
 * {@code market.transformation.MarketInflectionEngine}. Never touches a {@code Clock} - every
 * {@code asOfDate} here is a real evidence date, since only {@code SECTOR_RELATIVE_STRENGTH} is
 * reliably daily-cadence (unlike Market's 3 metrics, all genuinely daily); a {@code Clock}-based
 * date would manufacture apparent persistence from a stale {@code VS_NIFTY}/{@code VS_SECTOR}
 * observation on days that metric got no new real evidence at all.
 *
 * <p>{@code NEW_LEADERSHIP_EMERGENCE} needs no self-referential "read own prior state" lookup
 * (unlike Market's {@code EARLY_PRICE_PARTICIPATION}, a 3-metric composite with no single evidence
 * row that encodes it) - {@code STOCK_OUTPERFORMING_SECTOR} is a direct single-metric pass-through,
 * and Stage 1's own evidence row already carries the immediately-prior real day's value as
 * {@code priorValue}, so the crossing is derivable from that single row.
 */
@Component
class SectorInflectionEngine {

    private static final int SECTOR_STRENGTHENING_PERSISTENCE_CAP = 5;
    private static final double SECTOR_STRENGTHENING_BASE_CONFIDENCE = 90.0;
    private static final double THINNESS_PENALTY = 10.0;

    SectorInflectionResult calculate(
        UUID instrumentId, String symbol,
        SectorEvidenceObservation sectorRelativeStrength, SectorEvidenceObservation vsNifty, SectorEvidenceObservation vsSector,
        int vsNiftyObservationCount, int vsSectorObservationCount
    ) {
        boolean sectorStrengthening = changePositive(sectorRelativeStrength);
        boolean stockOutperformingNifty = valuePositive(vsNifty);
        boolean stockOutperformingSector = valuePositive(vsSector);
        // A fresh crossing needs a real prior value to cross from - a first-ever observation that
        // happens to be positive is STOCK_OUTPERFORMING_SECTOR, not a "crossing" (nothing to cross).
        boolean newLeadershipEmergence = stockOutperformingSector
            && vsSector.priorValue() != null && vsSector.priorValue().signum() <= 0;

        List<ReasonCode> reasons = new ArrayList<>();
        if (sectorStrengthening) {
            reasons.add(ReasonCode.of("SECTOR_RS_RISING", sectorRelativeStrength.change().doubleValue()));
        }
        if (stockOutperformingNifty) {
            reasons.add(ReasonCode.of("OUTPERFORMING_NIFTY_20D", vsNifty.value().doubleValue()));
        }
        if (stockOutperformingSector) {
            reasons.add(ReasonCode.of("OUTPERFORMING_SECTOR_20D", vsSector.value().doubleValue()));
        }
        if (newLeadershipEmergence) {
            reasons.add(ReasonCode.of("SECTOR_LEADERSHIP_CROSSING", vsSector.value().doubleValue()));
        }

        SectorInflectionState primaryState;
        if (newLeadershipEmergence) {
            primaryState = SectorInflectionState.NEW_LEADERSHIP_EMERGENCE;
        } else if (stockOutperformingSector) {
            primaryState = SectorInflectionState.STOCK_OUTPERFORMING_SECTOR;
        } else if (stockOutperformingNifty) {
            primaryState = SectorInflectionState.STOCK_OUTPERFORMING_NIFTY;
        } else if (sectorStrengthening) {
            primaryState = SectorInflectionState.SECTOR_STRENGTHENING;
        } else {
            primaryState = SectorInflectionState.NO_CLEAR_SIGNAL;
        }

        int evidenceCount = countNonNull(sectorRelativeStrength, vsNifty, vsSector);
        int evidenceCoveragePct = Math.round(evidenceCount / 3.0f * 100);
        SectorDataReadiness dataReadiness = evidenceCount == 3 ? SectorDataReadiness.READY
            : evidenceCount == 0 ? SectorDataReadiness.INSUFFICIENT_DATA : SectorDataReadiness.PARTIAL_DATA;

        return buildResult(
            primaryState, instrumentId, symbol, sectorRelativeStrength, vsNifty, vsSector,
            vsNiftyObservationCount, vsSectorObservationCount, evidenceCoveragePct, dataReadiness, reasons
        );
    }

    private SectorInflectionResult buildResult(
        SectorInflectionState primaryState, UUID instrumentId, String symbol,
        SectorEvidenceObservation sectorRelativeStrength, SectorEvidenceObservation vsNifty, SectorEvidenceObservation vsSector,
        int vsNiftyObservationCount, int vsSectorObservationCount,
        int evidenceCoveragePct, SectorDataReadiness dataReadiness, List<ReasonCode> reasons
    ) {
        SectorMetric drivingMetric;
        BigDecimal level;
        BigDecimal change;
        VelocityBand velocityBand;
        int persistence;
        LocalDate asOfDate;
        double confidence;

        switch (primaryState) {
            case SECTOR_STRENGTHENING -> {
                drivingMetric = SectorMetric.SECTOR_RELATIVE_STRENGTH;
                level = sectorRelativeStrength.value();
                change = sectorRelativeStrength.change();
                velocityBand = SectorVelocityBanding.bandPercentagePoint(change);
                persistence = Math.min(SECTOR_STRENGTHENING_PERSISTENCE_CAP, sectorRelativeStrength.persistenceDays());
                asOfDate = sectorRelativeStrength.asOfDate();
                boolean hasPrior = sectorRelativeStrength.priorAsOfDate() != null;
                confidence = drivingConfidence(SECTOR_STRENGTHENING_BASE_CONFIDENCE, persistence, hasPrior);
            }
            case STOCK_OUTPERFORMING_NIFTY -> {
                drivingMetric = SectorMetric.INSTRUMENT_RELATIVE_STRENGTH_VS_NIFTY;
                level = vsNifty.value();
                change = vsNifty.change();
                velocityBand = change == null ? null : SectorVelocityBanding.bandPercentagePoint(change);
                persistence = vsNifty.persistenceDays();
                asOfDate = vsNifty.asOfDate();
                boolean hasPrior = vsNifty.priorAsOfDate() != null;
                confidence = drivingConfidence(depthTierBase(vsNiftyObservationCount), persistence, hasPrior);
            }
            case STOCK_OUTPERFORMING_SECTOR -> {
                drivingMetric = SectorMetric.INSTRUMENT_RELATIVE_STRENGTH_VS_SECTOR;
                level = vsSector.value();
                change = vsSector.change();
                velocityBand = change == null ? null : SectorVelocityBanding.bandPercentagePoint(change);
                persistence = vsSector.persistenceDays();
                asOfDate = vsSector.asOfDate();
                boolean hasPrior = vsSector.priorAsOfDate() != null;
                confidence = drivingConfidence(depthTierBase(vsSectorObservationCount), persistence, hasPrior);
            }
            // Persistence is 0, not a shortcut - a crossing event, never sustained, same reasoning
            // as market.transformation.MarketInflectionEngine's EARLY_PRICE_PARTICIPATION.
            // hasPrior is unconditionally true - the fire condition itself requires a real
            // non-null priorValue to cross from (see calculate()).
            case NEW_LEADERSHIP_EMERGENCE -> {
                drivingMetric = SectorMetric.INSTRUMENT_RELATIVE_STRENGTH_VS_SECTOR;
                level = vsSector.value();
                change = vsSector.change();
                velocityBand = change == null ? null : SectorVelocityBanding.bandPercentagePoint(change);
                persistence = 0;
                asOfDate = vsSector.asOfDate();
                confidence = drivingConfidence(depthTierBase(vsSectorObservationCount), persistence, true);
            }
            default -> {
                drivingMetric = null;
                level = null;
                change = null;
                velocityBand = null;
                persistence = 0;
                asOfDate = maxAsOfDate(sectorRelativeStrength, vsNifty, vsSector);
                confidence = averageConfidence(sectorRelativeStrength, vsNifty, vsSector);
            }
        }

        return new SectorInflectionResult(
            instrumentId, symbol, asOfDate, primaryState, confidence,
            drivingMetric, level, change, velocityBand, persistence,
            evidenceCoveragePct, dataReadiness, reasons
        );
    }

    /** 0 -> 40, 1-4 -> 50, 5-19 -> 60, 20+ -> 75 - real total observations ever recorded for the driving metric, never a consecutive-streak count (which is structurally always 0 for NEW_LEADERSHIP_EMERGENCE, defeating the point of scaling confidence by real depth). */
    private static double depthTierBase(int observationCount) {
        if (observationCount >= 20) {
            return 75.0;
        }
        if (observationCount >= 5) {
            return 60.0;
        }
        if (observationCount >= 1) {
            return 50.0;
        }
        return 40.0;
    }

    private static double drivingConfidence(double base, int persistence, boolean hasPrior) {
        return clamp(base + Math.min(10.0, 2.0 * persistence) - (hasPrior ? 0.0 : THINNESS_PENALTY), 0.0, 100.0);
    }

    private static boolean changePositive(SectorEvidenceObservation observation) {
        return observation != null && observation.change() != null && observation.change().signum() > 0;
    }

    private static boolean valuePositive(SectorEvidenceObservation observation) {
        return observation != null && observation.value() != null && observation.value().signum() > 0;
    }

    private static int countNonNull(SectorEvidenceObservation... observations) {
        int count = 0;
        for (SectorEvidenceObservation observation : observations) {
            if (observation != null) {
                count++;
            }
        }
        return count;
    }

    private static LocalDate maxAsOfDate(SectorEvidenceObservation... observations) {
        LocalDate max = null;
        for (SectorEvidenceObservation observation : observations) {
            if (observation != null && (max == null || observation.asOfDate().isAfter(max))) {
                max = observation.asOfDate();
            }
        }
        return max;
    }

    private static double averageConfidence(SectorEvidenceObservation... observations) {
        double sum = 0;
        int count = 0;
        for (SectorEvidenceObservation observation : observations) {
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
