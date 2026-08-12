package com.alphagraph.learning.snapshot;

import com.alphagraph.decision.api.DecisionScore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Writes into learning.decision_snapshots - unlike {@code decision.engine.DecisionScoreStore}
 * (which upserts ON CONFLICT DO UPDATE, since decision_scores is a live, correctable view), this
 * store ON CONFLICT DOES NOTHING: once a day is archived, it stays exactly as first captured even
 * if decision_scores is later corrected for that date.
 */
@Component
class DecisionSnapshotStore {

    private final JdbcTemplate jdbcTemplate;

    DecisionSnapshotStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void archive(DecisionScore score) {
        jdbcTemplate.update(
            """
            INSERT INTO learning.decision_snapshots (
                id, instrument_id, symbol, as_of_date, swing_score, swing_rating, swing_rank,
                long_term_score, long_term_rating, long_term_rank,
                technical_score, fundamental_score, institutional_score, sector_score, risk_score, corporate_score,
                confidence, rule_set_version, decision_computed_at, captured_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id, as_of_date) DO NOTHING
            """,
            UUID.randomUUID(), score.instrumentId(), score.symbol(), Date.valueOf(score.asOfDate()),
            score.swingScore(), score.swingRating().name(), score.swingRank(),
            score.longTermScore(), score.longTermRating().name(), score.longTermRank(),
            score.technicalScore(), score.fundamentalScore(), score.institutionalScore(),
            score.sectorScore(), score.riskScore(), score.corporateScore(),
            score.confidence(), score.ruleSetVersion(), Timestamp.from(score.computedAt()), Timestamp.from(Instant.now())
        );
    }
}
