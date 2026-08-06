package com.alphagraph.decision.engine;

import com.alphagraph.decision.api.DecisionScore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.UUID;

/**
 * Writes new {@link DecisionScore}s - one row per (instrument, day), upserting on a same-day
 * re-run, mirroring every prior Layer 2 snapshot store (e.g. corporate.signal.CorporateScoreStore).
 * The upsert's DO UPDATE SET deliberately excludes swing_rank/long_term_rank: a single
 * instrument's own calculation never knows its rank (that's a cross-instrument concern), so this
 * write always carries rank as whatever the caller passed in (null on first write of a run) and
 * relies on {@code DecisionScoringOrchestrator} running its ranking pass once, after every
 * instrument for the day has been written - not on this store re-deriving it per row.
 */
@Component
class DecisionScoreStore {

    private final JdbcTemplate jdbcTemplate;

    DecisionScoreStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void write(DecisionScore score) {
        jdbcTemplate.update(
            """
            INSERT INTO decision.decision_scores (
                id, instrument_id, symbol, as_of_date, swing_score, swing_rating, swing_rank,
                long_term_score, long_term_rating, long_term_rank,
                technical_score, fundamental_score, institutional_score, sector_score, risk_score, corporate_score,
                confidence, rule_set_version, computed_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id, as_of_date) DO UPDATE SET
                symbol = EXCLUDED.symbol,
                swing_score = EXCLUDED.swing_score,
                swing_rating = EXCLUDED.swing_rating,
                long_term_score = EXCLUDED.long_term_score,
                long_term_rating = EXCLUDED.long_term_rating,
                technical_score = EXCLUDED.technical_score,
                fundamental_score = EXCLUDED.fundamental_score,
                institutional_score = EXCLUDED.institutional_score,
                sector_score = EXCLUDED.sector_score,
                risk_score = EXCLUDED.risk_score,
                corporate_score = EXCLUDED.corporate_score,
                confidence = EXCLUDED.confidence,
                rule_set_version = EXCLUDED.rule_set_version,
                computed_at = EXCLUDED.computed_at
            """,
            UUID.randomUUID(), score.instrumentId(), score.symbol(), Date.valueOf(score.asOfDate()),
            score.swingScore(), score.swingRating().name(), score.swingRank(),
            score.longTermScore(), score.longTermRating().name(), score.longTermRank(),
            score.technicalScore(), score.fundamentalScore(), score.institutionalScore(),
            score.sectorScore(), score.riskScore(), score.corporateScore(),
            score.confidence(), score.ruleSetVersion(), Timestamp.from(score.computedAt())
        );
    }
}
