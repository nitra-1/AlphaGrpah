package com.alphagraph.ownership.pattern;

import com.alphagraph.common.etl.Loader;
import com.alphagraph.ownership.api.ShareholdingPattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Upserts on (instrument_id, period_end) — idempotent per docs/002_Engine_Architecture.md §2.
 *
 * <p>{@code fii_percentage}/{@code dii_percentage}/{@code mf_percentage} use
 * {@code COALESCE(EXCLUDED.x, existing x)} on conflict, not a plain overwrite: a live-derived row
 * always carries null FII/DII (only XBRL enrichment fills those in, later and separately - see
 * {@code XbrlEnrichmentOrchestrator}), so a plain {@code EXCLUDED.fii_percentage} would silently
 * erase an already-enriched value back to null every time the daily collector re-runs.
 * {@code promoter_percentage}/{@code public_percentage} are always sourced fresh from the daily
 * feed, so those stay a plain overwrite.
 */
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
                promoter_percentage = EXCLUDED.promoter_percentage,
                fii_percentage = COALESCE(EXCLUDED.fii_percentage, ownership.shareholding_pattern.fii_percentage),
                dii_percentage = COALESCE(EXCLUDED.dii_percentage, ownership.shareholding_pattern.dii_percentage),
                mf_percentage = COALESCE(EXCLUDED.mf_percentage, ownership.shareholding_pattern.mf_percentage),
                public_percentage = EXCLUDED.public_percentage
            """,
            UUID.randomUUID(), pattern.instrumentId(), pattern.periodEnd(), pattern.promoterPercentage(),
            pattern.fiiPercentage(), pattern.diiPercentage(), pattern.mfPercentage(), pattern.publicPercentage()
        );
    }
}
