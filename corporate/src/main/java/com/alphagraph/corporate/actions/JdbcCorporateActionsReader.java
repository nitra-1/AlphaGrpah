package com.alphagraph.corporate.actions;

import com.alphagraph.corporate.api.CorporateAction;
import com.alphagraph.corporate.api.CorporateActionsReader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
public class JdbcCorporateActionsReader implements CorporateActionsReader {

    private static final RowMapper<CorporateAction> MAPPER = (rs, rowNum) -> new CorporateAction(
        (UUID) rs.getObject("instrument_id"), rs.getString("symbol"), rs.getString("action_type"),
        rs.getDate("ex_date").toLocalDate(),
        rs.getDate("record_date") == null ? null : rs.getDate("record_date").toLocalDate(),
        rs.getDate("announcement_date") == null ? null : rs.getDate("announcement_date").toLocalDate(),
        rs.getBigDecimal("dividend_amount"),
        rs.getObject("ratio_numerator") == null ? null : rs.getInt("ratio_numerator"),
        rs.getObject("ratio_denominator") == null ? null : rs.getInt("ratio_denominator"),
        rs.getBigDecimal("price")
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcCorporateActionsReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<CorporateAction> findPriceAffectingActions(UUID instrumentId) {
        return jdbcTemplate.query(
            """
            SELECT ca.instrument_id, i.symbol, ca.action_type, ca.ex_date, ca.record_date,
                   ca.announcement_date, ca.dividend_amount, ca.ratio_numerator, ca.ratio_denominator, ca.price
            FROM corporate.corporate_actions ca
            JOIN reference.instruments i ON i.id = ca.instrument_id
            WHERE ca.instrument_id = ? AND ca.action_type IN ('BONUS', 'SPLIT')
            ORDER BY ca.ex_date ASC
            """,
            MAPPER, instrumentId
        );
    }

    @Override
    public List<CorporateAction> findAllPriceAffectingActions() {
        return jdbcTemplate.query(
            """
            SELECT ca.instrument_id, i.symbol, ca.action_type, ca.ex_date, ca.record_date,
                   ca.announcement_date, ca.dividend_amount, ca.ratio_numerator, ca.ratio_denominator, ca.price
            FROM corporate.corporate_actions ca
            JOIN reference.instruments i ON i.id = ca.instrument_id
            WHERE ca.action_type IN ('BONUS', 'SPLIT')
            ORDER BY ca.instrument_id, ca.ex_date ASC
            """,
            MAPPER
        );
    }

    @Override
    public List<CorporateAction> findRecentPriceAffectingActions(int lookbackDays) {
        LocalDate since = LocalDate.now().minusDays(lookbackDays);
        return jdbcTemplate.query(
            """
            SELECT ca.instrument_id, i.symbol, ca.action_type, ca.ex_date, ca.record_date,
                   ca.announcement_date, ca.dividend_amount, ca.ratio_numerator, ca.ratio_denominator, ca.price
            FROM corporate.corporate_actions ca
            JOIN reference.instruments i ON i.id = ca.instrument_id
            WHERE ca.action_type IN ('BONUS', 'SPLIT') AND ca.ex_date >= ?
            ORDER BY ca.ex_date DESC
            """,
            MAPPER, since
        );
    }
}
