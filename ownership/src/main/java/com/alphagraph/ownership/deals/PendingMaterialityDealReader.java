package com.alphagraph.ownership.deals;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * The driving set for {@link DealMaterialityScoringOrchestrator}: every {@code discovered_deals}
 * row with no {@code deal_materiality} row yet. A deal that's skipped this run for insufficient
 * price history (see {@link MarketLiquidityReader}) simply reappears here on the next run - it's
 * never marked "attempted" anywhere, so a symbol that later crosses the 20-session threshold gets
 * scored automatically without any separate retry bookkeeping.
 */
@Component
class PendingMaterialityDealReader {

    private final JdbcTemplate jdbcTemplate;

    PendingMaterialityDealReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<PendingMaterialityDeal> findUnscored() {
        return jdbcTemplate.query(
            """
            SELECT d.id, d.symbol, d.deal_date, d.deal_value, d.buy_sell, d.client_name_normalized
            FROM ownership.discovered_deals d
            WHERE NOT EXISTS (SELECT 1 FROM ownership.deal_materiality m WHERE m.discovered_deal_id = d.id)
            ORDER BY d.deal_date, d.symbol
            """,
            (rs, rowNum) -> new PendingMaterialityDeal(
                (UUID) rs.getObject("id"), rs.getString("symbol"), rs.getDate("deal_date").toLocalDate(),
                rs.getBigDecimal("deal_value"), rs.getString("buy_sell"), rs.getString("client_name_normalized")
            )
        );
    }
}
