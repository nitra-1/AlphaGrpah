package com.alphagraph.corporate.commentary;

import com.alphagraph.corporate.api.ManagementCommentarySnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.UUID;

/** Writes new {@link ManagementCommentarySnapshot}s - one row per (instrument, day), upserting on a same-day re-run, mirroring {@code corporate.orderbook.OrderBookSnapshotStore}. */
@Component
class ManagementSnapshotStore {

    private final JdbcTemplate jdbcTemplate;

    ManagementSnapshotStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void write(ManagementCommentarySnapshot snapshot) {
        jdbcTemplate.update(
            """
            INSERT INTO corporate.management_commentary_snapshots (
                id, instrument_id, symbol, as_of_date, growth_visibility_score, guidance_trend,
                management_credibility, confidence, rule_set_version, computed_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id, as_of_date) DO UPDATE SET
                growth_visibility_score = EXCLUDED.growth_visibility_score,
                guidance_trend = EXCLUDED.guidance_trend,
                management_credibility = EXCLUDED.management_credibility,
                confidence = EXCLUDED.confidence,
                rule_set_version = EXCLUDED.rule_set_version,
                computed_at = EXCLUDED.computed_at
            """,
            UUID.randomUUID(), snapshot.instrumentId(), snapshot.symbol(), Date.valueOf(snapshot.asOfDate()),
            snapshot.growthVisibilityScore(), snapshot.guidanceTrend().name(), snapshot.managementCredibility().name(),
            snapshot.confidence(), snapshot.ruleSetVersion(), Timestamp.from(snapshot.computedAt())
        );
    }
}
