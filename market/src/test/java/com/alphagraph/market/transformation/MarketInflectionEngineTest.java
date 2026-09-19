package com.alphagraph.market.transformation;

import com.alphagraph.common.inflection.VelocityBand;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MarketInflectionEngineTest {

    private static final UUID INSTRUMENT_ID = UUID.randomUUID();
    private static final String SYMBOL = "RELIANCE";
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 19);

    private final MarketInflectionEngine engine = new MarketInflectionEngine();

    @Test
    void deliveryExpansionFiresOnPositiveChange() {
        var delivery = obs(MarketMetric.DELIVERY_PERCENTAGE_20D_AVG, "65.00", "64.00", "1.00", "1.00", 3);
        var relVol = obs(MarketMetric.RELATIVE_VOLUME, "0.9", "0.9", "0", "0", 0);

        var result = engine.calculate(INSTRUMENT_ID, SYMBOL, TODAY, delivery, relVol, null, null);

        assertThat(result.primaryState()).isEqualTo(MarketInflectionState.DELIVERY_EXPANSION);
        assertThat(result.drivingMetric()).isEqualTo(MarketMetric.DELIVERY_PERCENTAGE_20D_AVG);
        assertThat(result.level()).isEqualByComparingTo("65.00");
        assertThat(result.change()).isEqualByComparingTo("1.00");
        assertThat(result.velocityBand()).isEqualTo(VelocityBand.MODERATE);
        assertThat(result.persistence()).isEqualTo(3);
        assertThat(result.confidence()).isEqualTo(96.0);
    }

    @Test
    void relativeVolumeExpansionRequiresValueAboveOne() {
        var delivery = obs(MarketMetric.DELIVERY_PERCENTAGE_20D_AVG, "60.00", "60.20", "-0.20", "-0.20", 0);
        var relVol = obs(MarketMetric.RELATIVE_VOLUME, "1.5", "1.2", "0.3", "0.3", 2);

        var result = engine.calculate(INSTRUMENT_ID, SYMBOL, TODAY, delivery, relVol, null, null);

        assertThat(result.primaryState()).isEqualTo(MarketInflectionState.RELATIVE_VOLUME_EXPANSION);
        assertThat(result.drivingMetric()).isEqualTo(MarketMetric.RELATIVE_VOLUME);
        assertThat(result.velocityBand()).isEqualTo(VelocityBand.MODERATE);
        assertThat(result.persistence()).isEqualTo(2);
        assertThat(result.confidence()).isEqualTo(94.0);
    }

    @Test
    void relativeVolumeBelowOneNeverFiresEvenWithPositiveChange() {
        var relVol = obs(MarketMetric.RELATIVE_VOLUME, "0.8", "0.5", "0.3", "0.3", 1);

        var result = engine.calculate(INSTRUMENT_ID, SYMBOL, TODAY, null, relVol, null, null);

        assertThat(result.primaryState()).isEqualTo(MarketInflectionState.NO_CLEAR_SIGNAL);
    }

    @Test
    void sustainedDeliveryAccumulationNeedsPersistenceAtLeastFive() {
        var deliveryPersistentEnough = obs(MarketMetric.DELIVERY_PERCENTAGE_20D_AVG, "70.00", "69.40", "0.60", "0.60", 5);
        var resultSustained = engine.calculate(INSTRUMENT_ID, SYMBOL, TODAY, deliveryPersistentEnough, null, null, null);
        assertThat(resultSustained.primaryState()).isEqualTo(MarketInflectionState.SUSTAINED_DELIVERY_ACCUMULATION);

        var deliveryNotPersistentEnough = obs(MarketMetric.DELIVERY_PERCENTAGE_20D_AVG, "70.00", "69.40", "0.60", "0.60", 4);
        var resultNotSustained = engine.calculate(INSTRUMENT_ID, SYMBOL, TODAY, deliveryNotPersistentEnough, null, null, null);
        assertThat(resultNotSustained.primaryState()).isEqualTo(MarketInflectionState.DELIVERY_EXPANSION);
    }

    @Test
    void stealthAccumulationCandidateRequiresPriceWithinQuietZone() {
        var delivery = obs(MarketMetric.DELIVERY_PERCENTAGE_20D_AVG, "70.00", "69.40", "0.60", "0.60", 3);
        var relVol = obs(MarketMetric.RELATIVE_VOLUME, "1.3", "1.0", "0.3", "0.3", 2);
        var priceReturnQuiet = obs(MarketMetric.PRICE_RETURN_20D, "2.5", "2.0", "0.5", "0.5", 1);

        var result = engine.calculate(INSTRUMENT_ID, SYMBOL, TODAY, delivery, relVol, priceReturnQuiet, null);

        assertThat(result.primaryState()).isEqualTo(MarketInflectionState.STEALTH_ACCUMULATION_CANDIDATE);
        assertThat(result.drivingMetric()).isEqualTo(MarketMetric.DELIVERY_PERCENTAGE_20D_AVG);
        assertThat(result.persistence()).isEqualTo(2); // min(delivery=3, relVol=2)

        var priceReturnOutsideZone = obs(MarketMetric.PRICE_RETURN_20D, "4.0", "3.0", "1.0", "1.0", 1);
        var resultOutsideZone = engine.calculate(INSTRUMENT_ID, SYMBOL, TODAY, delivery, relVol, priceReturnOutsideZone, null);
        assertThat(resultOutsideZone.primaryState()).isEqualTo(MarketInflectionState.DELIVERY_EXPANSION);
    }

    @Test
    void earlyPriceParticipationFiresOnlyWhenPriorStealthAndPriceEmergesAboveQuietZone() {
        var breakout = obs(MarketMetric.PRICE_RETURN_20D, "3.2", "2.8", "0.4", "0.4", 1);
        var result = engine.calculate(INSTRUMENT_ID, SYMBOL, TODAY, null, null, breakout, "STEALTH_ACCUMULATION_CANDIDATE");
        assertThat(result.primaryState()).isEqualTo(MarketInflectionState.EARLY_PRICE_PARTICIPATION);
        assertThat(result.drivingMetric()).isEqualTo(MarketMetric.PRICE_RETURN_20D);
        assertThat(result.persistence()).isEqualTo(0);
    }

    @Test
    void earlyPriceParticipationDoesNotFireOnALargeChangeThatStillLandsInsideTheQuietZone() {
        // The key corrected behavior: a huge one-day change (+3.7pp) that still leaves the value
        // inside the +/-3% quiet zone (1.2%) must NOT be mistaken for a breakout - the state is
        // about the price having emerged out of the zone, not the size of the day's move.
        var stillQuiet = obs(MarketMetric.PRICE_RETURN_20D, "1.2", "-2.5", "3.7", "3.7", 1);

        var result = engine.calculate(INSTRUMENT_ID, SYMBOL, TODAY, null, null, stillQuiet, "STEALTH_ACCUMULATION_CANDIDATE");

        assertThat(result.primaryState()).isEqualTo(MarketInflectionState.NO_CLEAR_SIGNAL);
    }

    @Test
    void earlyPriceParticipationRequiresARealPriorStealthState() {
        var breakout = obs(MarketMetric.PRICE_RETURN_20D, "3.2", "2.8", "0.4", "0.4", 1);

        var noPriorState = engine.calculate(INSTRUMENT_ID, SYMBOL, TODAY, null, null, breakout, null);
        assertThat(noPriorState.primaryState()).isEqualTo(MarketInflectionState.NO_CLEAR_SIGNAL);

        var wrongPriorState = engine.calculate(INSTRUMENT_ID, SYMBOL, TODAY, null, null, breakout, "DELIVERY_EXPANSION");
        assertThat(wrongPriorState.primaryState()).isEqualTo(MarketInflectionState.NO_CLEAR_SIGNAL);
    }

    @Test
    void earlyPriceParticipationDoesNotFireWhenPriceIsRetreating() {
        // Value is still above the quiet zone (3.2%) but the day's change is negative - retreating,
        // not a fresh breakout.
        var retreating = obs(MarketMetric.PRICE_RETURN_20D, "3.2", "3.6", "-0.4", "-0.4", 1);

        var result = engine.calculate(INSTRUMENT_ID, SYMBOL, TODAY, null, null, retreating, "STEALTH_ACCUMULATION_CANDIDATE");

        assertThat(result.primaryState()).isEqualTo(MarketInflectionState.NO_CLEAR_SIGNAL);
    }

    @Test
    void noClearSignalFallsBackToAveragedEvidenceConfidence() {
        var delivery = obs(MarketMetric.DELIVERY_PERCENTAGE_20D_AVG, "60.00", "60.20", "-0.20", "-0.20", 0, 90.0);
        var relVol = new MarketEvidenceObservation(
            MarketMetric.RELATIVE_VOLUME, INSTRUMENT_ID, SYMBOL, TODAY, null,
            new BigDecimal("0.9"), null, null, null, 0, 40.0
        );

        var result = engine.calculate(INSTRUMENT_ID, SYMBOL, TODAY, delivery, relVol, null, null);

        assertThat(result.primaryState()).isEqualTo(MarketInflectionState.NO_CLEAR_SIGNAL);
        assertThat(result.drivingMetric()).isNull();
        assertThat(result.level()).isNull();
        assertThat(result.persistence()).isEqualTo(0);
        assertThat(result.confidence()).isEqualTo(65.0);
    }

    private static MarketEvidenceObservation obs(MarketMetric metric, String value, String priorValue, String change, String velocity, int persistence) {
        return obs(metric, value, priorValue, change, velocity, persistence, 90.0);
    }

    private static MarketEvidenceObservation obs(MarketMetric metric, String value, String priorValue, String change, String velocity, int persistence, double confidence) {
        return new MarketEvidenceObservation(
            metric, INSTRUMENT_ID, SYMBOL, TODAY, TODAY.minusDays(1),
            new BigDecimal(value), new BigDecimal(priorValue), new BigDecimal(change), new BigDecimal(velocity),
            persistence, confidence
        );
    }
}
