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
                confidence, rule_set_version, computed_at,
                technical_score_as_of_date, technical_rule_set_version, technical_computed_at,
                fundamental_score_as_of_date, fundamental_rule_set_version, fundamental_computed_at,
                institutional_score_as_of_date, institutional_rule_set_version, institutional_computed_at,
                sector_score_as_of_date, sector_rule_set_version, sector_computed_at,
                risk_score_as_of_date, risk_rule_set_version, risk_computed_at,
                corporate_score_as_of_date, corporate_rule_set_version, corporate_computed_at,
                decision_run_id, swing_rank_universe_size, long_term_rank_universe_size
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                computed_at = EXCLUDED.computed_at,
                technical_score_as_of_date = EXCLUDED.technical_score_as_of_date,
                technical_rule_set_version = EXCLUDED.technical_rule_set_version,
                technical_computed_at = EXCLUDED.technical_computed_at,
                fundamental_score_as_of_date = EXCLUDED.fundamental_score_as_of_date,
                fundamental_rule_set_version = EXCLUDED.fundamental_rule_set_version,
                fundamental_computed_at = EXCLUDED.fundamental_computed_at,
                institutional_score_as_of_date = EXCLUDED.institutional_score_as_of_date,
                institutional_rule_set_version = EXCLUDED.institutional_rule_set_version,
                institutional_computed_at = EXCLUDED.institutional_computed_at,
                sector_score_as_of_date = EXCLUDED.sector_score_as_of_date,
                sector_rule_set_version = EXCLUDED.sector_rule_set_version,
                sector_computed_at = EXCLUDED.sector_computed_at,
                risk_score_as_of_date = EXCLUDED.risk_score_as_of_date,
                risk_rule_set_version = EXCLUDED.risk_rule_set_version,
                risk_computed_at = EXCLUDED.risk_computed_at,
                corporate_score_as_of_date = EXCLUDED.corporate_score_as_of_date,
                corporate_rule_set_version = EXCLUDED.corporate_rule_set_version,
                corporate_computed_at = EXCLUDED.corporate_computed_at,
                decision_run_id = EXCLUDED.decision_run_id
            """,
            UUID.randomUUID(), score.instrumentId(), score.symbol(), Date.valueOf(score.asOfDate()),
            score.swingScore(), score.swingRating().name(), score.swingRank(),
            score.longTermScore(), score.longTermRating().name(), score.longTermRank(),
            score.technicalScore(), score.fundamentalScore(), score.institutionalScore(),
            score.sectorScore(), score.riskScore(), score.corporateScore(),
            score.confidence(), score.ruleSetVersion(), Timestamp.from(score.computedAt()),
            toDate(score.technicalScoreAsOfDate()), score.technicalRuleSetVersion(), toTimestamp(score.technicalComputedAt()),
            toDate(score.fundamentalScoreAsOfDate()), score.fundamentalRuleSetVersion(), toTimestamp(score.fundamentalComputedAt()),
            toDate(score.institutionalScoreAsOfDate()), score.institutionalRuleSetVersion(), toTimestamp(score.institutionalComputedAt()),
            toDate(score.sectorScoreAsOfDate()), score.sectorRuleSetVersion(), toTimestamp(score.sectorComputedAt()),
            toDate(score.riskScoreAsOfDate()), score.riskRuleSetVersion(), toTimestamp(score.riskComputedAt()),
            toDate(score.corporateScoreAsOfDate()), score.corporateRuleSetVersion(), toTimestamp(score.corporateComputedAt()),
            score.decisionRunId(), score.swingRankUniverseSize(), score.longTermRankUniverseSize()
        );
    }

    private static Date toDate(java.time.LocalDate date) {
        return date == null ? null : Date.valueOf(date);
    }

    private static Timestamp toTimestamp(java.time.Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
