package com.alphagraph.market.transformation;

import com.alphagraph.market.api.DailyPrice;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure calculation - no JDBC, no I/O - same convention as
 * {@code ownership.transformation.OwnershipTransformationEngine}. Unlike Ownership's quarterly
 * walk, market data is daily and (barring the very start of an instrument's history) never has
 * gaps between {@code prior} and {@code current}, so {@code computeObservation} always compares
 * the two most recent rows directly rather than walking back to the nearest non-null one.
 *
 * <p>{@code persistenceDays} needs a short trailing series of the metric's own day-by-day values,
 * not just two points - {@link #PERSISTENCE_WINDOW_DAYS} bounds how far back that series is
 * recomputed, so this stays a cheap nightly batch calculation even over years of daily history.
 */
@Component
class MarketAccumulationEngine {

    static final int LOOKBACK_DAYS = 20;
    private static final int PERSISTENCE_WINDOW_DAYS = 20;
    private static final double CONFIDENCE = 90.0;
    private static final double FIRST_OBSERVATION_CONFIDENCE = 40.0;

    List<MarketEvidenceObservation> calculate(UUID instrumentId, String symbol, List<DailyPrice> pricesAscending) {
        if (pricesAscending.size() < LOOKBACK_DAYS + 1) {
            return List.of();
        }

        List<MarketEvidenceObservation> evidence = new ArrayList<>();
        for (MarketMetric metric : MarketMetric.values()) {
            computeObservation(metric, instrumentId, symbol, pricesAscending).ifPresent(evidence::add);
        }
        return evidence;
    }

    private Optional<MarketEvidenceObservation> computeObservation(
        MarketMetric metric, UUID instrumentId, String symbol, List<DailyPrice> pricesAscending
    ) {
        List<BigDecimal> series = metricSeries(metric, pricesAscending);
        if (series.isEmpty()) {
            return Optional.empty();
        }

        int n = pricesAscending.size();
        var current = pricesAscending.get(n - 1);
        BigDecimal currentValue = series.get(series.size() - 1);

        if (series.size() < 2) {
            return Optional.of(new MarketEvidenceObservation(
                metric, instrumentId, symbol, current.tradeDate(), null, currentValue, null, null, null, 0, FIRST_OBSERVATION_CONFIDENCE
            ));
        }

        var prior = pricesAscending.get(n - 2);
        BigDecimal priorValue = series.get(series.size() - 2);
        BigDecimal change = currentValue.subtract(priorValue);
        int persistence = persistenceDays(series, change.signum());

        return Optional.of(new MarketEvidenceObservation(
            metric, instrumentId, symbol, current.tradeDate(), prior.tradeDate(),
            currentValue, priorValue, change, change, persistence, CONFIDENCE
        ));
    }

    /** The metric's own value for each of the trailing (up to) {@code PERSISTENCE_WINDOW_DAYS + 1} trading days, ascending. */
    private static List<BigDecimal> metricSeries(MarketMetric metric, List<DailyPrice> pricesAscending) {
        int n = pricesAscending.size();
        int seriesStart = Math.max(LOOKBACK_DAYS, n - 1 - PERSISTENCE_WINDOW_DAYS);

        List<BigDecimal> series = new ArrayList<>();
        for (int i = seriesStart; i < n; i++) {
            Optional<BigDecimal> value = computeMetricValue(metric, pricesAscending.subList(0, i + 1));
            if (value.isEmpty()) {
                return List.of();
            }
            series.add(value.get());
        }
        return series;
    }

    private static Optional<BigDecimal> computeMetricValue(MarketMetric metric, List<DailyPrice> pricesAscendingThroughDay) {
        return switch (metric) {
            case RELATIVE_VOLUME -> RelativeVolumeCalculator.of(pricesAscendingThroughDay.stream().map(DailyPrice::volume).toList(), LOOKBACK_DAYS);
            case DELIVERY_PERCENTAGE_20D_AVG -> DeliveryPercentageAverage.of(pricesAscendingThroughDay.stream().map(DailyPrice::deliveryPercentage).toList(), LOOKBACK_DAYS);
            case PRICE_RETURN_20D -> PriceReturnCalculator.of(pricesAscendingThroughDay.stream().map(DailyPrice::close).toList(), LOOKBACK_DAYS);
        };
    }

    /** Counts consecutive same-sign day-over-day transitions in the series, walking backward from the most recent one, including it. */
    private static int persistenceDays(List<BigDecimal> seriesAscending, int currentSign) {
        if (currentSign == 0) {
            return 0;
        }
        int count = 0;
        for (int i = seriesAscending.size() - 1; i > 0; i--) {
            int sign = seriesAscending.get(i).subtract(seriesAscending.get(i - 1)).signum();
            if (sign != currentSign) {
                break;
            }
            count++;
        }
        return count;
    }
}
