package com.alphagraph.corporate.signal;

import com.alphagraph.corporate.api.CorporateScore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.UUID;

/** Writes new {@link CorporateScore}s - one row per (instrument, day), upserting on a same-day re-run, mirroring every prior Layer 2 snapshot store. */
@Component
class CorporateScoreStore {

    private final JdbcTemplate jdbcTemplate;

    CorporateScoreStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void write(CorporateScore score) {
        jdbcTemplate.update(
            """
            INSERT INTO corporate.corporate_scores (
                id, instrument_id, symbol, as_of_date, corporate_score, corporate_rating,
                order_book_score, management_score, news_catalyst_score, event_net_signal,
                confidence, rule_set_version, computed_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id, as_of_date) DO UPDATE SET
                corporate_score = EXCLUDED.corporate_score,
                corporate_rating = EXCLUDED.corporate_rating,
                order_book_score = EXCLUDED.order_book_score,
                management_score = EXCLUDED.management_score,
                news_catalyst_score = EXCLUDED.news_catalyst_score,
                event_net_signal = EXCLUDED.event_net_signal,
                confidence = EXCLUDED.confidence,
                rule_set_version = EXCLUDED.rule_set_version,
                computed_at = EXCLUDED.computed_at
            """,
            UUID.randomUUID(), score.instrumentId(), score.symbol(), Date.valueOf(score.asOfDate()),
            score.corporateScore(), score.corporateRating().name(),
            score.orderBookScore(), score.managementScore(), score.newsCatalystScore(), score.eventNetSignal(),
            score.confidence(), score.ruleSetVersion(), Timestamp.from(score.computedAt())
        );
    }
}
