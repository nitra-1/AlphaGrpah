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
                confidence, rule_set_version, decision_computed_at,
                technical_score_as_of_date, technical_rule_set_version, technical_computed_at,
                fundamental_score_as_of_date, fundamental_rule_set_version, fundamental_computed_at,
                institutional_score_as_of_date, institutional_rule_set_version, institutional_computed_at,
                sector_score_as_of_date, sector_rule_set_version, sector_computed_at,
                risk_score_as_of_date, risk_rule_set_version, risk_computed_at,
                corporate_score_as_of_date, corporate_rule_set_version, corporate_computed_at,
                decision_run_id, swing_rank_universe_size, long_term_rank_universe_size,
                captured_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id, as_of_date) DO NOTHING
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
            score.decisionRunId(), score.swingRankUniverseSize(), score.longTermRankUniverseSize(),
            Timestamp.from(Instant.now())
        );
    }

    private static Date toDate(java.time.LocalDate date) {
        return date == null ? null : Date.valueOf(date);
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
