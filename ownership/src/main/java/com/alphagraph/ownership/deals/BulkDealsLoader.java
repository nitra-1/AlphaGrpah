package com.alphagraph.ownership.deals;

import com.alphagraph.common.etl.Loader;
import com.alphagraph.ownership.api.BulkDeal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Upserts on (instrument_id, deal_date, client_name, buy_sell, deal_type) — idempotent per docs/002_Engine_Architecture.md §2. */
@Component
public class BulkDealsLoader implements Loader<BulkDeal> {

    private final JdbcTemplate jdbcTemplate;

    public BulkDealsLoader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void load(BulkDeal deal) {
        jdbcTemplate.update(
            """
            INSERT INTO ownership.bulk_deals
                (id, instrument_id, deal_date, client_name, buy_sell, quantity, price, deal_type)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id, deal_date, client_name, buy_sell, deal_type) DO UPDATE SET
                quantity = EXCLUDED.quantity, price = EXCLUDED.price
            """,
            UUID.randomUUID(), deal.instrumentId(), deal.dealDate(), deal.clientName(),
            deal.buySell(), deal.quantity(), deal.price(), deal.dealType()
        );
    }
}
