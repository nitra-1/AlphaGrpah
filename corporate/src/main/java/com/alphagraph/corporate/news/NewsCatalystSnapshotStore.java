package com.alphagraph.corporate.news;

import com.alphagraph.corporate.api.NewsCatalystSnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.UUID;

/** Writes new {@link NewsCatalystSnapshot}s - one row per (instrument, day), upserting on a same-day re-run, mirroring {@code corporate.commentary.ManagementSnapshotStore}. */
@Component
class NewsCatalystSnapshotStore {

    private final JdbcTemplate jdbcTemplate;

    NewsCatalystSnapshotStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void write(NewsCatalystSnapshot snapshot) {
        jdbcTemplate.update(
            """
            INSERT INTO corporate.news_catalyst_scores (
                id, instrument_id, symbol, as_of_date, catalyst_score, catalyst_trend,
                recent_catalyst_count, confidence, rule_set_version, computed_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id, as_of_date) DO UPDATE SET
                catalyst_score = EXCLUDED.catalyst_score,
                catalyst_trend = EXCLUDED.catalyst_trend,
                recent_catalyst_count = EXCLUDED.recent_catalyst_count,
                confidence = EXCLUDED.confidence,
                rule_set_version = EXCLUDED.rule_set_version,
                computed_at = EXCLUDED.computed_at
            """,
            UUID.randomUUID(), snapshot.instrumentId(), snapshot.symbol(), Date.valueOf(snapshot.asOfDate()),
            snapshot.catalystScore(), snapshot.catalystTrend().name(), snapshot.recentCatalystCount(),
            snapshot.confidence(), snapshot.ruleSetVersion(), Timestamp.from(snapshot.computedAt())
        );
    }
}
