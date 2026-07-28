package com.alphagraph.market.pricing;

import com.alphagraph.market.api.DailyPrice;
import com.alphagraph.market.api.DailyPriceReader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class JdbcDailyPriceReader implements DailyPriceReader {

    private final JdbcTemplate jdbcTemplate;

    public JdbcDailyPriceReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<UUID> instrumentIdsWithHistory() {
        return jdbcTemplate.query(
            "SELECT DISTINCT instrument_id FROM market.daily_prices",
            (rs, rowNum) -> (UUID) rs.getObject("instrument_id")
        );
    }

    @Override
    public List<DailyPrice> findHistory(UUID instrumentId) {
        return jdbcTemplate.query(
            """
            SELECT dp.instrument_id, i.symbol, dp.trade_date, dp.open_price, dp.high_price,
                   dp.low_price, dp.close_price, dp.volume, dp.delivery_percentage
            FROM market.daily_prices dp
            JOIN reference.instruments i ON i.id = dp.instrument_id
            WHERE dp.instrument_id = ?
            ORDER BY dp.trade_date ASC
            """,
            (rs, rowNum) -> new DailyPrice(
                (UUID) rs.getObject("instrument_id"), rs.getString("symbol"), rs.getDate("trade_date").toLocalDate(),
                rs.getBigDecimal("open_price"), rs.getBigDecimal("high_price"), rs.getBigDecimal("low_price"),
                rs.getBigDecimal("close_price"), rs.getLong("volume"), rs.getBigDecimal("delivery_percentage")
            ),
            instrumentId
        );
    }
}
