package com.alphagraph.ownership.deals;

import com.alphagraph.ownership.api.DiscoveredDealDetail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Individual deals for one symbol's Discovery expand-on-click section - a plain
 * {@code LEFT JOIN} onto {@code deal_materiality} (not aggregated, unlike {@link DiscoveryReader}'s
 * symbol-level roll-up), newest deal first, so a deal that hasn't been scored yet still appears
 * with null materiality fields rather than being hidden. Unlike every aggregate reader, this one
 * deliberately does *not* filter out {@code duplicate_of_deal_id IS NOT NULL} rows - every raw
 * disclosed deal stays visible here for audit (see V11's migration comment); {@code isDuplicate}
 * lets the UI flag it instead of hiding it.
 */
@Component
public class DealDetailReader {

    private final JdbcTemplate jdbcTemplate;

    public DealDetailReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DiscoveredDealDetail> findDealsForSymbol(String symbol) {
        return jdbcTemplate.query(
            """
            SELECT d.id, d.deal_date, d.client_name, d.buy_sell, d.quantity, d.price, d.deal_value, d.deal_type,
                   d.duplicate_of_deal_id, m.materiality_score, m.materiality_level, m.deal_to_adtv_ratio, m.reported_flow_state
            FROM ownership.discovered_deals d
            LEFT JOIN ownership.deal_materiality m ON m.discovered_deal_id = d.id
            WHERE d.symbol = ?
            ORDER BY d.deal_date DESC, d.deal_value DESC
            """,
            (rs, rowNum) -> new DiscoveredDealDetail(
                (UUID) rs.getObject("id"), rs.getDate("deal_date").toLocalDate(), rs.getString("client_name"),
                rs.getString("buy_sell"), rs.getLong("quantity"), rs.getBigDecimal("price"), rs.getBigDecimal("deal_value"),
                rs.getString("deal_type"), rs.getObject("duplicate_of_deal_id") != null,
                toDouble(rs.getBigDecimal("materiality_score")), rs.getString("materiality_level"),
                toDouble(rs.getBigDecimal("deal_to_adtv_ratio")), rs.getString("reported_flow_state")
            ),
            symbol
        );
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
