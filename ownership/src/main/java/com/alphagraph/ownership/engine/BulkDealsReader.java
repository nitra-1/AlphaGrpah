package com.alphagraph.ownership.engine;

import com.alphagraph.ownership.api.BulkDeal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Reads ownership.bulk_deals directly - it's this module's own table. */
@Component
public class BulkDealsReader {

    private final JdbcTemplate jdbcTemplate;

    public BulkDealsReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<BulkDeal> findRecentDeals(UUID instrumentId) {
        return jdbcTemplate.query(
            """
            SELECT bd.instrument_id, i.symbol, bd.deal_date, bd.client_name, bd.buy_sell,
                   bd.quantity, bd.price, bd.deal_type
            FROM ownership.bulk_deals bd
            JOIN reference.instruments i ON i.id = bd.instrument_id
            WHERE bd.instrument_id = ?
            ORDER BY bd.deal_date ASC
            """,
            (rs, rowNum) -> new BulkDeal(
                (UUID) rs.getObject("instrument_id"), rs.getString("symbol"), rs.getDate("deal_date").toLocalDate(),
                rs.getString("client_name"), rs.getString("buy_sell"), rs.getLong("quantity"),
                rs.getBigDecimal("price"), rs.getString("deal_type")
            ),
            instrumentId
        );
    }
}
