package com.alphagraph.decision.engine;

import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.corporate.api.CorporateScore;
import com.alphagraph.corporate.signal.CorporateScoreReader;
import com.alphagraph.decision.api.DecisionScore;
import com.alphagraph.financial.api.FundamentalScore;
import com.alphagraph.financial.engine.FundamentalScoreReader;
import com.alphagraph.ownership.api.InstitutionalScore;
import com.alphagraph.ownership.engine.InstitutionalScoreReader;
import com.alphagraph.risk.api.RiskScore;
import com.alphagraph.risk.engine.RiskScoreReader;
import com.alphagraph.sector.api.SectorScore;
import com.alphagraph.sector.engine.SectorScoreReader;
import com.alphagraph.technical.api.TechnicalScore;
import com.alphagraph.technical.engine.TechnicalScoreReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Recomputes every instrument with at least one domain score on record - the driving set is the
 * union of instrument_ids across technical_scores/fundamental_scores/institutional_scores/
 * risk_scores/corporate_scores (sector_score is looked up per-instrument via its sector, not part
 * of the union - a sector-only match without any of the other five would only ever produce a
 * near-meaningless score), the same "don't fabricate a score for an instrument with no real
 * signal anywhere" pattern corporate.signal.CorporateSignalOrchestrator established.
 *
 * <p>Runs after every domain engine's own scheduler for the day (see {@link DecisionScoringScheduler}),
 * then a ranking pass: {@link #assignRanks} orders every instrument scored today by swing/long-term
 * score and writes swing_rank/long_term_rank back - a cross-instrument concern the single-instrument
 * {@link DecisionScoringEngine} cannot resolve on its own.
 */
@Component
public class DecisionScoringOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(DecisionScoringOrchestrator.class);

    private final JdbcTemplate jdbcTemplate;
    private final TechnicalScoreReader technicalScoreReader;
    private final FundamentalScoreReader fundamentalScoreReader;
    private final InstitutionalScoreReader institutionalScoreReader;
    private final SectorScoreReader sectorScoreReader;
    private final RiskScoreReader riskScoreReader;
    private final CorporateScoreReader corporateScoreReader;
    private final DecisionRuleSetLoader ruleSetLoader;
    private final DecisionScoringEngine engine;
    private final DecisionScoreStore scoreStore;
    private final DecisionRunStore runStore;

    public DecisionScoringOrchestrator(
        JdbcTemplate jdbcTemplate, TechnicalScoreReader technicalScoreReader,
        FundamentalScoreReader fundamentalScoreReader, InstitutionalScoreReader institutionalScoreReader,
        SectorScoreReader sectorScoreReader, RiskScoreReader riskScoreReader, CorporateScoreReader corporateScoreReader,
        DecisionRuleSetLoader ruleSetLoader, DecisionScoringEngine engine, DecisionScoreStore scoreStore,
        DecisionRunStore runStore
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.technicalScoreReader = technicalScoreReader;
        this.fundamentalScoreReader = fundamentalScoreReader;
        this.institutionalScoreReader = institutionalScoreReader;
        this.sectorScoreReader = sectorScoreReader;
        this.riskScoreReader = riskScoreReader;
        this.corporateScoreReader = corporateScoreReader;
        this.ruleSetLoader = ruleSetLoader;
        this.engine = engine;
        this.scoreStore = scoreStore;
        this.runStore = runStore;
    }

    public void recomputeAllInstruments() {
        List<UUID> instrumentIds = findDrivingSet();
        LocalDate asOfDate = LocalDate.now();
        RuleSet rules = ruleSetLoader.loadActiveRules();
        UUID runId = runStore.startOrResume(asOfDate, rules.version());

        try {
            int updated = 0;
            for (UUID instrumentId : instrumentIds) {
                try {
                    recomputeOne(instrumentId, asOfDate, rules, runId);
                    updated++;
                } catch (Exception e) {
                    log.error("Failed to recompute decision score for instrument {}: {}", instrumentId, e.getMessage(), e);
                }
            }
            assignRanks(asOfDate);
            int rankedCount = countRankedForDate(asOfDate);
            runStore.markCompleted(runId, instrumentIds.size(), rankedCount);
            log.info("Decision Scoring update: {} instrument scores recomputed, {} ranked (of {} candidates)", updated, rankedCount, instrumentIds.size());
        } catch (Exception e) {
            runStore.markFailed(runId);
            throw e;
        }
    }

    private int countRankedForDate(LocalDate asOfDate) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM decision.decision_scores WHERE as_of_date = ? AND swing_rank IS NOT NULL",
            Integer.class, Date.valueOf(asOfDate)
        );
        return count == null ? 0 : count;
    }

    private List<UUID> findDrivingSet() {
        return jdbcTemplate.query(
            """
            SELECT DISTINCT instrument_id FROM (
                SELECT instrument_id FROM technical.technical_scores
                UNION SELECT instrument_id FROM financial.fundamental_scores
                UNION SELECT instrument_id FROM ownership.institutional_scores
                UNION SELECT instrument_id FROM risk.risk_scores
                UNION SELECT instrument_id FROM corporate.corporate_scores
            ) driving_set
            """,
            (rs, rowNum) -> (UUID) rs.getObject("instrument_id")
        );
    }

    private void recomputeOne(UUID instrumentId, LocalDate asOfDate, RuleSet rules, UUID runId) {
        Optional<TechnicalScore> technical = technicalScoreReader.findAsOf(instrumentId, asOfDate);
        Optional<FundamentalScore> fundamental = fundamentalScoreReader.findAsOf(instrumentId, asOfDate);
        Optional<InstitutionalScore> institutional = institutionalScoreReader.findAsOf(instrumentId, asOfDate);
        Optional<SectorScore> sector = sectorScoreReader.findAsOfForInstrument(instrumentId, asOfDate);
        Optional<RiskScore> risk = riskScoreReader.findAsOf(instrumentId, asOfDate);
        Optional<CorporateScore> corporate = corporateScoreReader.findAsOf(instrumentId, asOfDate);

        String symbol = resolveSymbol(technical, fundamental, institutional, risk, corporate, instrumentId);

        DecisionScoringInput input = new DecisionScoringInput(
            instrumentId, symbol,
            technical.map(TechnicalScore::marketBehaviourScore).orElse(null),
            fundamental.map(FundamentalScore::financialScore).orElse(null),
            institutional.map(InstitutionalScore::institutionalScore).orElse(null),
            sector.map(SectorScore::sectorScore).orElse(null),
            risk.map(RiskScore::riskScore).orElse(null),
            corporate.map(CorporateScore::corporateScore).orElse(null),
            asOfDate,
            technical.map(TechnicalScore::asOfDate).orElse(null), technical.map(TechnicalScore::ruleSetVersion).orElse(null), technical.map(TechnicalScore::computedAt).orElse(null),
            fundamental.map(FundamentalScore::asOfDate).orElse(null), fundamental.map(FundamentalScore::ruleSetVersion).orElse(null), fundamental.map(FundamentalScore::computedAt).orElse(null),
            institutional.map(InstitutionalScore::asOfDate).orElse(null), institutional.map(InstitutionalScore::ruleSetVersion).orElse(null), institutional.map(InstitutionalScore::computedAt).orElse(null),
            sector.map(SectorScore::asOfDate).orElse(null), sector.map(SectorScore::ruleSetVersion).orElse(null), sector.map(SectorScore::computedAt).orElse(null),
            risk.map(RiskScore::asOfDate).orElse(null), risk.map(RiskScore::ruleSetVersion).orElse(null), risk.map(RiskScore::computedAt).orElse(null),
            corporate.map(CorporateScore::asOfDate).orElse(null), corporate.map(CorporateScore::ruleSetVersion).orElse(null), corporate.map(CorporateScore::computedAt).orElse(null),
            runId
        );

        DecisionScore score = engine.calculate(input, rules);
        scoreStore.write(score);
    }

    private String resolveSymbol(
        Optional<TechnicalScore> technical, Optional<FundamentalScore> fundamental,
        Optional<InstitutionalScore> institutional, Optional<RiskScore> risk,
        Optional<CorporateScore> corporate, UUID instrumentId
    ) {
        if (technical.isPresent()) {
            return technical.get().symbol();
        }
        if (fundamental.isPresent()) {
            return fundamental.get().symbol();
        }
        if (institutional.isPresent()) {
            return institutional.get().symbol();
        }
        if (risk.isPresent()) {
            return risk.get().symbol();
        }
        if (corporate.isPresent()) {
            return corporate.get().symbol();
        }
        return jdbcTemplate.queryForObject("SELECT symbol FROM reference.instruments WHERE id = ?", String.class, instrumentId);
    }

    /** Dense rank, highest score first, scoped to the given date - ties share a rank, matching standard leaderboard semantics. COUNT(*) OVER () alongside it records how many instruments shared that day's ranking, so a rank like "#4" can be read against its actual universe size rather than assumed. */
    private void assignRanks(LocalDate asOfDate) {
        jdbcTemplate.update(
            """
            UPDATE decision.decision_scores d SET swing_rank = ranked.rnk, swing_rank_universe_size = ranked.universe_size
            FROM (
                SELECT id, DENSE_RANK() OVER (ORDER BY swing_score DESC) AS rnk, COUNT(*) OVER () AS universe_size
                FROM decision.decision_scores WHERE as_of_date = ?
            ) ranked
            WHERE d.id = ranked.id
            """,
            Date.valueOf(asOfDate)
        );
        jdbcTemplate.update(
            """
            UPDATE decision.decision_scores d SET long_term_rank = ranked.rnk, long_term_rank_universe_size = ranked.universe_size
            FROM (
                SELECT id, DENSE_RANK() OVER (ORDER BY long_term_score DESC) AS rnk, COUNT(*) OVER () AS universe_size
                FROM decision.decision_scores WHERE as_of_date = ?
            ) ranked
            WHERE d.id = ranked.id
            """,
            Date.valueOf(asOfDate)
        );
    }
}
