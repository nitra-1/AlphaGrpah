package com.alphagraph.ownership.pattern;

import com.alphagraph.common.etl.Loader;
import com.alphagraph.ownership.api.ShareholdingPattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Upserts on (instrument_id, period_end) — idempotent per docs/002_Engine_Architecture.md §2. */
@Component
public class ShareholdingLoader implements Loader<ShareholdingPattern> {

    private final JdbcTemplate jdbcTemplate;

    public ShareholdingLoader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void load(ShareholdingPattern pattern) {
        jdbcTemplate.update(
            """
            INSERT INTO ownership.shareholding_pattern
                (id, instrument_id, period_end, promoter_percentage, fii_percentage, dii_percentage, mf_percentage, public_percentage)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id, period_end) DO UPDATE SET
                promoter_percentage = EXCLUDED.promoter_percentage, fii_percentage = EXCLUDED.fii_percentage,
                dii_percentage = EXCLUDED.dii_percentage, mf_percentage = EXCLUDED.mf_percentage,
                public_percentage = EXCLUDED.public_percentage
            """,
            UUID.randomUUID(), pattern.instrumentId(), pattern.periodEnd(), pattern.promoterPercentage(),
            pattern.fiiPercentage(), pattern.diiPercentage(), pattern.mfPercentage(), pattern.publicPercentage()
        );
    }
}
