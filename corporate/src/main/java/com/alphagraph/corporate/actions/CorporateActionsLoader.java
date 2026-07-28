package com.alphagraph.corporate.actions;

import com.alphagraph.common.etl.Loader;
import com.alphagraph.corporate.api.CorporateAction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Upserts on (instrument_id, action_type, ex_date) — idempotent per docs/002_Engine_Architecture.md §2. */
@Component
public class CorporateActionsLoader implements Loader<CorporateAction> {

    private final JdbcTemplate jdbcTemplate;

    public CorporateActionsLoader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void load(CorporateAction action) {
        jdbcTemplate.update(
            """
            INSERT INTO corporate.corporate_actions
                (id, instrument_id, action_type, ex_date, record_date, announcement_date,
                 dividend_amount, ratio_numerator, ratio_denominator, price)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id, action_type, ex_date) DO UPDATE SET
                record_date = EXCLUDED.record_date, announcement_date = EXCLUDED.announcement_date,
                dividend_amount = EXCLUDED.dividend_amount, ratio_numerator = EXCLUDED.ratio_numerator,
                ratio_denominator = EXCLUDED.ratio_denominator, price = EXCLUDED.price
            """,
            UUID.randomUUID(), action.instrumentId(), action.actionType(), action.exDate(),
            action.recordDate(), action.announcementDate(), action.dividendAmount(),
            action.ratioNumerator(), action.ratioDenominator(), action.price()
        );
    }
}
