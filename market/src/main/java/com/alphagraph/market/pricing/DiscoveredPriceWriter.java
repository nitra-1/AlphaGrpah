package com.alphagraph.market.pricing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Persists one day's OHLCV for a symbol {@link BhavdataNormalizer} confirmed is a genuine
 * Discovery candidate (via {@link DiscoveryCandidateLookup}) but can't resolve to a tracked
 * instrument. Real trading history for these symbols is what makes a 20-trading-day ADTV
 * computable at all - see {@code ownership.deals.MarketLiquidityReader}.
 *
 * <p>{@code dailyTradedValue} is NSE's own real turnover ({@code TURNOVER_LACS * 100000}, in real
 * rupees) - genuine exchange-reported turnover, not a {@code close * volume} estimate.
 *
 * <p>Deliberately best-effort, mirroring {@code ownership.deals.DiscoveredDealWriter}: any
 * failure here (a malformed numeric field, a transient DB issue) is caught and logged, never
 * propagated - a bug in this side-channel capture must never suppress or change the real "Unknown
 * instrument" rejection the caller is about to throw.
 */
@Component
class DiscoveredPriceWriter {

    private static final Logger log = LoggerFactory.getLogger(DiscoveredPriceWriter.class);
    private static final BigDecimal LACS_TO_RUPEES = BigDecimal.valueOf(100_000);

    private final JdbcTemplate jdbcTemplate;

    DiscoveredPriceWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void capture(RawDeliveryRow raw) {
        try {
            LocalDate tradeDate = LocalDate.parse(raw.tradeDate(), BhavdataNormalizer.DATE_FORMAT);
            BigDecimal open = new BigDecimal(raw.open());
            BigDecimal high = new BigDecimal(raw.high());
            BigDecimal low = new BigDecimal(raw.low());
            BigDecimal close = new BigDecimal(raw.close());
            long volume = Long.parseLong(raw.volume());
            BigDecimal dailyTradedValue = blankToNull(raw.turnoverLacs()) == null
                ? null
                : new BigDecimal(raw.turnoverLacs()).multiply(LACS_TO_RUPEES);
            BigDecimal deliveryPercentage = blankToNull(raw.deliveryPercentage()) == null
                ? null
                : new BigDecimal(raw.deliveryPercentage());

            jdbcTemplate.update(
                """
                INSERT INTO market.discovered_prices
                    (id, symbol, trade_date, open_price, high_price, low_price, close_price, volume, daily_traded_value, delivery_percentage)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (symbol, trade_date) DO UPDATE SET
                    open_price = EXCLUDED.open_price, high_price = EXCLUDED.high_price,
                    low_price = EXCLUDED.low_price, close_price = EXCLUDED.close_price,
                    volume = EXCLUDED.volume, daily_traded_value = EXCLUDED.daily_traded_value,
                    delivery_percentage = EXCLUDED.delivery_percentage
                """,
                UUID.randomUUID(), raw.symbol(), tradeDate, open, high, low, close, volume, dailyTradedValue, deliveryPercentage
            );
        } catch (Exception e) {
            log.warn("Failed to capture discovered price for symbol {}: {}", raw.symbol(), e.getMessage());
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
