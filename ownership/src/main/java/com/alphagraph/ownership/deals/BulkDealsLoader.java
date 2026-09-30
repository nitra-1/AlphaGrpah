package com.alphagraph.ownership.deals;

import com.alphagraph.common.etl.Loader;
import com.alphagraph.ownership.api.BulkDeal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Upserts on (instrument_id, deal_date, client_name, buy_sell, deal_type) — idempotent per
 * docs/002_Engine_Architecture.md §2. A no-op for {@link Optional#empty()} - {@link
 * BulkDealsNormalizer} already routed that row to {@code ownership.discovered_deals} (Discovery)
 * instead, there's nothing left for this loader to do.
 */
@Component
public class BulkDealsLoader implements Loader<Optional<BulkDeal>> {

    private final JdbcTemplate jdbcTemplate;

    public BulkDealsLoader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void load(Optional<BulkDeal> maybeDeal) {
        if (maybeDeal.isEmpty()) {
            return;
        }
        BulkDeal deal = maybeDeal.get();
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
