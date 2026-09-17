package com.alphagraph.market.transformation;

import com.alphagraph.market.api.DailyPrice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MarketAccumulationEngineTest {

    private static final UUID INSTRUMENT_ID = UUID.randomUUID();
    private static final String SYMBOL = "RELIANCE";

    private final MarketAccumulationEngine engine = new MarketAccumulationEngine();

    @Test
    void tooShortHistoryProducesNoEvidence() {
        List<DailyPrice> prices = flatHistory(10, 100, 1000);

        List<MarketEvidenceObservation> evidence = engine.calculate(INSTRUMENT_ID, SYMBOL, prices);

        assertThat(evidence).isEmpty();
    }

    @Test
    void firstComputableObservationHasNullPriorAndLowerConfidence() {
        // exactly LOOKBACK_DAYS + 1 rows - just enough for one metric value, not two.
        List<DailyPrice> prices = flatHistory(MarketAccumulationEngine.LOOKBACK_DAYS + 1, 100, 1000);

        List<MarketEvidenceObservation> evidence = engine.calculate(INSTRUMENT_ID, SYMBOL, prices);

        assertThat(evidence).hasSize(3);
        for (MarketEvidenceObservation observation : evidence) {
            assertThat(observation.priorTradeDate()).isNull();
            assertThat(observation.priorValue()).isNull();
            assertThat(observation.change()).isNull();
            assertThat(observation.confidence()).isEqualTo(40.0);
        }
    }

    @Test
    void volumeSpikeOnTheMostRecentDayRaisesRelativeVolumeAboveOne() {
        List<DailyPrice> prices = new ArrayList<>(flatHistory(MarketAccumulationEngine.LOOKBACK_DAYS + 5, 100, 1000));
        DailyPrice last = prices.remove(prices.size() - 1);
        prices.add(new DailyPrice(
            last.instrumentId(), last.symbol(), last.tradeDate(), last.open(), last.high(), last.low(), last.close(),
            5000, last.deliveryPercentage()
        ));

        List<MarketEvidenceObservation> evidence = engine.calculate(INSTRUMENT_ID, SYMBOL, prices);

        MarketEvidenceObservation relativeVolume = evidence.stream()
            .filter(o -> o.metric() == MarketMetric.RELATIVE_VOLUME)
            .findFirst().orElseThrow();
        assertThat(relativeVolume.value()).isGreaterThan(BigDecimal.ONE);
        assertThat(relativeVolume.change().signum()).isPositive();
    }

    @Test
    void priceReturnReflectsCloseVsTwentySessionsAgo() {
        List<DailyPrice> prices = risingCloseHistory(MarketAccumulationEngine.LOOKBACK_DAYS + 2, 100.0, 1.0);

        List<MarketEvidenceObservation> evidence = engine.calculate(INSTRUMENT_ID, SYMBOL, prices);

        MarketEvidenceObservation priceReturn = evidence.stream()
            .filter(o -> o.metric() == MarketMetric.PRICE_RETURN_20D)
            .findFirst().orElseThrow();
        assertThat(priceReturn.value().signum()).isPositive();
    }

    private static List<DailyPrice> flatHistory(int days, long volume, double closeCents) {
        List<DailyPrice> prices = new ArrayList<>();
        LocalDate start = LocalDate.of(2026, 1, 1);
        BigDecimal close = BigDecimal.valueOf(closeCents);
        for (int i = 0; i < days; i++) {
            prices.add(new DailyPrice(INSTRUMENT_ID, SYMBOL, start.plusDays(i), close, close, close, close, volume, BigDecimal.valueOf(50)));
        }
        return prices;
    }

    private static List<DailyPrice> risingCloseHistory(int days, double startClose, double dailyIncrement) {
        List<DailyPrice> prices = new ArrayList<>();
        LocalDate start = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < days; i++) {
            BigDecimal close = BigDecimal.valueOf(startClose + (dailyIncrement * i));
            prices.add(new DailyPrice(INSTRUMENT_ID, SYMBOL, start.plusDays(i), close, close, close, close, 1000, BigDecimal.valueOf(50)));
        }
        return prices;
    }
}
