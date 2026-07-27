package com.alphagraph.market.pricing;

import com.alphagraph.common.etl.Loader;
import com.alphagraph.market.api.DailyPrice;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Upserts on (instrument_id, trade_date) — idempotent per docs/002_Engine_Architecture.md §2. */
@Component
public class DailyPriceLoader implements Loader<DailyPrice> {

    private final JdbcTemplate jdbcTemplate;

    public DailyPriceLoader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void load(DailyPrice price) {
        jdbcTemplate.update(
            """
            INSERT INTO market.daily_prices
                (id, instrument_id, trade_date, open_price, high_price, low_price, close_price, volume, delivery_percentage)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id, trade_date) DO UPDATE SET
                open_price = EXCLUDED.open_price, high_price = EXCLUDED.high_price,
                low_price = EXCLUDED.low_price, close_price = EXCLUDED.close_price,
                volume = EXCLUDED.volume, delivery_percentage = EXCLUDED.delivery_percentage
            """,
            UUID.randomUUID(), price.instrumentId(), price.tradeDate(), price.open(), price.high(),
            price.low(), price.close(), price.volume(), price.deliveryPercentage()
        );
    }
}
